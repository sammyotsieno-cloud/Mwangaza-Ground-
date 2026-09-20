package org.mwangaza.app.scanner.interpretation

import core.domain.model.ProductType
import org.mwangaza.app.scanner.BarcodeResult
import org.mwangaza.app.scanner.OcrResult
import org.mwangaza.app.scanner.ProductScanDraft
import org.mwangaza.app.scanner.ProductScanObservation
import org.mwangaza.app.scanner.ReconciledFinding
import java.util.Locale

data class IdentityCandidate(
    val field: String,
    val value: String,
    val confidence: Float,
    val evidence: List<String>,
    val conflictingValues: List<String> = emptyList()
)

data class ProductIdentityInterpretation(
    val draft: ProductScanDraft,
    val candidates: List<IdentityCandidate>,
    val reconciliationFindings: List<ReconciledFinding> = emptyList()
)

object ProductIdentityInterpreter {
    fun interpret(productType: ProductType, ocr: List<OcrResult>, barcodes: List<BarcodeResult>): ProductIdentityInterpretation =
        interpret(productType, listOf(ProductScanObservation(
            sourceImageUri = ocr.firstOrNull()?.sourceImageUri ?: barcodes.firstOrNull()?.sourceImageUri ?: "unknown://observation",
            ocrResults = ocr,
            barcodeResults = barcodes
        )))

    fun interpret(productType: ProductType, observations: List<ProductScanObservation>): ProductIdentityInterpretation {
        if (observations.isEmpty()) return ProductIdentityInterpretation(ProductScanDraft(productType = productType), emptyList(), emptyList())
        val interpreted = observations.map { it to interpretSingle(productType, it.ocrResults, it.barcodeResults) }
        return reconcile(productType, interpreted)
    }

    private fun interpretSingle(
        productType: ProductType,
        ocr: List<OcrResult>,
        barcodes: List<BarcodeResult>
    ): ProductIdentityInterpretation {
        val lines = ocr.flatMap { result ->
            result.blocks.flatMap { block ->
                block.lines.map { it.text.trim() }
            }.ifEmpty { result.text.lines().map(String::trim) }
        }.filter { it.isNotBlank() }.distinct()

        val text = lines.joinToString("\n")
        val lower = text.lowercase(Locale.ROOT)

        val strengthRegex = Regex(
            """\b\d+(?:[.,]\d+)?\s*(?:mg|mcg|g|kg|ml|%)(?:\s*/\s*\d+(?:[.,]\d+)?\s*(?:ml|mL|g))?\b""",
            RegexOption.IGNORE_CASE
        )
        val strengthMatches = strengthRegex.findAll(text).map { it.value }.distinct().toList()

        val dosageForms = listOf(
            "tablet", "tablets", "tab", "tabs", "capsule", "capsules", "cap", "caps",
            "caplet", "caplets", "syrup", "suspension", "susp", "solution", "soln", "sol",
            "powder", "pwd", "sachet", "sachets", "granules", "cream", "cr", "ointment",
            "oint", "gel", "lotion", "injection", "inj", "vial", "ampoule", "ampule", "amp",
            "drops", "spray", "inhaler", "patch", "patches", "suppository", "suppositories",
            "supp", "pessary", "pessaries", "pess", "device", "kit", "strip", "strips",
            "lozenge", "lozenges", "film", "emulsion", "eye drops", "ear drops",
            "nasal spray", "nasal drops", "granule", "paste", "foam", "shampoo",
            "mouthwash", "rinse", "elixir", "tincture", "liniment", "paint",
            "dressing", "bandage", "gauze", "catheter", "cannula", "syringe",
            "needle", "gloves", "mask", "condom"
        )
        val dosageForm = dosageForms.firstOrNull {
            Regex("\\b" + Regex.escape(it) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
        }

        val explicitRoute = listOf(
            "Ophthalmic" to Regex("\\bophthalmic\\b|\\beye\\s+drops?\\b", RegexOption.IGNORE_CASE),
            "Otic" to Regex("\\botic\\b|\\bear\\s+drops?\\b", RegexOption.IGNORE_CASE),
            "Nasal" to Regex("\\bnasal\\b|\\bintranasal\\b", RegexOption.IGNORE_CASE),
            "Topical" to Regex("\\btopical\\b|\\bdermal\\b|\\bcutaneous\\b", RegexOption.IGNORE_CASE),
            "Transdermal" to Regex("\\btransdermal\\b", RegexOption.IGNORE_CASE),
            "Parenteral" to Regex("\\bparenteral\\b|\\bintravenous\\b|\\bintramuscular\\b|\\bsubcutaneous\\b", RegexOption.IGNORE_CASE),
            "Rectal" to Regex("\\brectal\\b", RegexOption.IGNORE_CASE),
            "Vaginal" to Regex("\\bvaginal\\b|\\bintravaginal\\b", RegexOption.IGNORE_CASE),
            "Oral" to Regex("\\boral\\b|\\bby\\s+mouth\\b|\\bper\\s+os\\b", RegexOption.IGNORE_CASE)
        ).firstOrNull { it.second.containsMatchIn(text) }?.first

        val inferredRoute = when (dosageForm?.lowercase(Locale.ROOT)) {
            "tablet", "tablets", "tab", "tabs", "capsule", "capsules", "cap", "caps", "caplet", "caplets",
            "syrup", "suspension", "susp", "solution", "soln", "sol", "powder", "pwd", "sachet", "sachets", "granules",
            "lozenge", "lozenges", "film" -> "Oral"
            "cream", "cr", "ointment", "oint", "gel", "lotion", "patch", "patches" -> "Topical"
            "injection", "inj", "vial", "ampoule", "ampule", "amp" -> "Parenteral"
            "eye drops" -> "Ophthalmic"
            "ear drops" -> "Otic"
            "nasal spray", "nasal drops" -> "Nasal"
            "suppository", "suppositories", "supp" -> "Rectal"
            "pessary", "pessaries", "pess" -> "Vaginal"
            else -> null
        }
        val route = explicitRoute ?: inferredRoute
        val routeSource = when {
            explicitRoute != null -> "EXPLICIT"
            inferredRoute != null -> "INFERRED"
            else -> null
        }

        val ocrLines = ocr.flatMap { result ->
            result.blocks.flatMap { block ->
                block.lines.map { line ->
                    Triple(line.text.trim(), line.bounds?.width()?.toFloat() ?: 0f, line.bounds?.top?.toFloat() ?: Float.MAX_VALUE)
                }
            }
        }.filter { it.first.isNotBlank() }

        val maxWidth = ocrLines.maxOfOrNull { it.second }?.takeIf { it > 0f } ?: 1f
        val minTop = ocrLines.minOfOrNull { it.third }?.takeIf { it != Float.MAX_VALUE } ?: 0f
        val maxTop = ocrLines.maxOfOrNull { it.third }?.takeIf { it != Float.MAX_VALUE } ?: minTop
        val topRange = (maxTop - minTop).coerceAtLeast(1f)

        val brandCandidates = lines
            .filter { it.length in 3..80 }
            .filterNot { line ->
                val l = line.lowercase(Locale.ROOT)
                strengthRegex.containsMatchIn(line) ||
                    dosageForms.any { Regex("\\b" + Regex.escape(it) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(line) } ||
                    listOf("manufactured", "manufacturer", "mfd.", "mfg.", "marketed", "distributed", "packed", "repacked", "produced", "made by", "licence", "license", "storage", "batch", "lot", "expiry", "exp", "mfg").any { l.contains(it) } ||
                    Regex("\\b(?:contains?|each|per|net|volume|for\\s+oral\\s+use)\\b", RegexOption.IGNORE_CASE).containsMatchIn(line)
            }
            .map { line ->
                val evidence = ocrLines.firstOrNull { it.first.equals(line, ignoreCase = true) }
                val relativeWidth = ((evidence?.second ?: 0f) / maxWidth).coerceIn(0f, 1f)
                val relativeTop = if (evidence == null || evidence.third == Float.MAX_VALUE) 0.5f else ((evidence.third - minTop) / topRange).coerceIn(0f, 1f)
                val score = (0.32f + relativeWidth * 0.30f + (1f - relativeTop) * 0.20f +
                    when { line.length in 3..24 -> 0.12f; line.length in 25..40 -> 0.05f; else -> 0f } -
                    if (line.count { it == ' ' } > 7) 0.12f else 0f).coerceIn(0.05f, 0.97f)
                IdentityCandidate("brandName", line, score, listOf("OCR_LINE", "RELATIVE_SIZE", "VERTICAL_POSITION", "NEGATIVE_FILTERS"))
            }
            .distinctBy { it.value.lowercase(Locale.ROOT) }
            .sortedByDescending { it.confidence }

        val manufacturerCues = listOf(
            "Manufactured by", "Mfd. by", "Mfd by", "Mfg. by", "Mfg by",
            "Manufactured for", "Marketed by", "Distributed by", "Packed by",
            "Repacked by", "Produced by", "Made by", "Under licence by", "Under license by"
        )
        val manufacturerMatch = manufacturerCues.asSequence().flatMap { cue ->
            lines.asSequence().mapNotNull { line ->
                Regex("(?i)\\b" + Regex.escape(cue) + "\\s*[:\\-]?\\s*(.+)$").find(line)
            }
        }.firstOrNull()
        val manufacturer = manufacturerMatch?.groupValues?.get(1)?.trim()?.trimEnd('.', ',', ';', ':')?.ifBlank { null }

        val active = lines.firstOrNull {
            it.lowercase(Locale.ROOT).contains("active ingredient")
        }?.substringAfter(":", "")?.trim()?.ifBlank { null }

        val prescription = when {
            Regex("\\bprescription only\\b|\\bPOM\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "Prescription Only"
            Regex("\\bover the counter\\b|\\bOTC\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "OTC"
            else -> null
        }

        val categoryExtraction = CategoryExtractionProfiles.extract(productType, ocr)
        val categoryStrength = categoryExtraction.variables.firstOrNull { it.definitionKey == "strength" }?.value

        val draft = ProductScanDraft(
            brandName = brandCandidates.firstOrNull()?.value,
            genericName = if (productType == ProductType.MEDICINE) {
                categoryExtraction.ingredients.firstOrNull()?.ingredientName ?: active
            } else null,
            productType = productType,
            manufacturer = manufacturer,
            dosageForm = dosageForm,
            route = route,
            routeSource = routeSource,
            strength = categoryStrength ?: strengthMatches.firstOrNull(),
            activeIngredients = active,
            ingredientProposals = categoryExtraction.ingredients,
            prescriptionClassification = prescription,
            storageCondition = categoryExtraction.variables.firstOrNull { it.definitionKey == "storage_condition" }?.value,
            categoryVariables = categoryExtraction.variables,
            barcodeValue = barcodes.firstOrNull()?.rawValue,
            barcodeFormat = barcodes.firstOrNull()?.format,
            otherDetectedText = text.ifBlank { null }
        )

        val ocrIdentifierCandidates = Regex("""(?<!\d)\d{8,14}(?!\d)""")
            .findAll(text)
            .map { it.value }
            .filter { isPlausibleOcrIdentifier(it) }
            .distinct()
            .map { raw ->
                IdentityCandidate(
                    field = "identifier",
                    value = raw,
                    confidence = 0.85f,
                    evidence = listOf("OCR_IDENTIFIER")
                )
            }
            .toList()

        val conflicts = brandCandidates.map { it.value }.distinct().drop(1)
        val candidates = brandCandidates.take(5).map {
            it.copy(conflictingValues = conflicts)
        } + (manufacturer?.let { listOf(IdentityCandidate("manufacturer", it, 0.97f, listOf("MANUFACTURER_CUE"))) } ?: emptyList()) + (dosageForm?.let { listOf(IdentityCandidate("productForm", it, 0.93f, listOf("OCR_FORM_KEYWORD"))) } ?: emptyList()) + ocrIdentifierCandidates + barcodes.map {
            IdentityCandidate(
                field = "identifier",
                value = it.rawValue,
                confidence = if (it.validationState == "PLAUSIBLE") 0.95f else 0.70f,
                evidence = listOf("BARCODE", it.format)
            )
        }

        return ProductIdentityInterpretation(draft, candidates)
    }

    private data class FC(val value:String,val norm:String,val uri:String,val evidence:List<String> = emptyList())

    private fun reconcile(type: ProductType, xs: List<Pair<ProductScanObservation, ProductIdentityInterpretation>>): ProductIdentityInterpretation {
        fun draft(field:(ProductScanDraft)->String?) = xs.mapNotNull { (o,i) -> field(i.draft)?.trim()?.takeIf{it.isNotBlank()}?.let{FC(it,norm(it),o.sourceImageUri)} }
        fun cand(field:String) = xs.flatMap { (o,i) -> i.candidates.filter{it.field==field}.map{FC(it.value,norm(it.value),o.sourceImageUri,it.evidence)} }
        fun one(field:String, values:List<FC>): Pair<String?,ReconciledFinding?> {
            if(values.isEmpty()) return null to null
            val g=values.groupBy{it.norm}; val conflict=g.size>1
            return (if(conflict)null else values.first().value) to ReconciledFinding(
                field=field,value=values.first().value,normalizedValue=if(conflict)null else values.first().norm,
                status=if(conflict)"CONFLICT" else if(values.size>1)"AGREEMENT" else "UNIQUE",
                sourceImageUris=values.map{it.uri}.distinct(),evidence=values.flatMap{it.evidence}.distinct(),
                conflictingValues=if(conflict)g.values.map{it.first().value}else emptyList())
        }
        val b=one("brandName",draft{it.brandName})
        val m=one("manufacturer",cand("manufacturer")+draft{it.manufacturer})
        val f=one("productForm",cand("productForm")+draft{it.dosageForm})
        val routeValues=draft{it.route}.mapIndexed { index, value ->
            val source=xs[index].second.draft.routeSource ?: "UNKNOWN"
            value.copy(evidence=value.evidence+source)
        }
        val explicitRoutes=routeValues.filter{it.evidence.contains("EXPLICIT")}
        val routePool=if(explicitRoutes.isNotEmpty()) explicitRoutes else routeValues
        val r=one("route",routePool)
        val pc=one("prescriptionClassification",draft{it.prescriptionClassification})
        val tc=one("therapeuticCategory",draft{it.therapeuticCategory})
        val sc=one("storageCondition",draft{it.storageCondition})
        val ids=xs.flatMap{(o,i)->i.candidates.filter{it.field=="identifier"}.map{FC(it.value,normId(it.value),o.sourceImageUri,it.evidence)}}
        val ig=ids.groupBy{it.normIdKey()}; val idConflict=ig.size>1
        val idValue=if(idConflict)null else ids.firstOrNull()?.value
        val idFinding=ids.takeIf{it.isNotEmpty()}?.let{ReconciledFinding("identifier",it.first().value,if(idConflict)null else it.first().norm,if(idConflict)"CONFLICT" else if(it.size>1)"AGREEMENT" else "UNIQUE",it.map{v->v.uri}.distinct(),it.flatMap{v->v.evidence}.distinct(),if(idConflict)ig.values.map{v->v.first().value}else emptyList())}
        val cats=reconcileCats(xs)
        val ings=reconcileIngs(xs)
        val findings=listOfNotNull(b.second,m.second,f.second,r.second,pc.second,tc.second,sc.second,idFinding)+cats.second+ings.second
        val routeSource=if(r.first!=null) {
            routePool.firstOrNull{norm(it.value)==norm(r.first!!)}?.evidence?.lastOrNull{it=="EXPLICIT"||it=="INFERRED"}
        } else null
        val strength=cats.first.firstOrNull{it.definitionKey=="strength"}?.value
        val draft=ProductScanDraft(
            brandName=b.first,genericName=ings.first.firstOrNull()?.ingredientName ?: xs.mapNotNull{it.second.draft.genericName}.firstOrNull(),
            productType=type,manufacturer=m.first,dosageForm=f.first,route=r.first,routeSource=routeSource,
            strength=strength,prescriptionClassification=pc.first,therapeuticCategory=tc.first,storageCondition=sc.first,
            activeIngredients=xs.mapNotNull{it.second.draft.activeIngredients}.firstOrNull(),ingredientProposals=ings.first,
            categoryVariables=cats.first,barcodeValue=idValue,
            barcodeFormat=xs.flatMap{it.second.draft.barcodeFormat?.let{v->listOf(v)}.orEmpty()}.distinct().singleOrNull(),
            otherDetectedText=xs.mapNotNull{it.second.draft.otherDetectedText}.joinToString("\n").ifBlank{null},
            sourceImageUris=xs.map{it.first.sourceImageUri}.distinct())
        val out=findings.map{IdentityCandidate(it.field,it.value,if(it.status=="CONFLICT")0.5f else 0.9f,it.evidence.ifEmpty{listOf(it.status)},it.conflictingValues)}
        return ProductIdentityInterpretation(draft,out,findings)
    }

    private fun reconcileCats(xs:List<Pair<ProductScanObservation,ProductIdentityInterpretation>>):Pair<List<org.mwangaza.app.scanner.interpretation.CategoryVariableProposal>,List<ReconciledFinding>>{
        val e=xs.flatMap{(o,i)->i.draft.categoryVariables.map{o to it}}
        val out=mutableListOf<org.mwangaza.app.scanner.interpretation.CategoryVariableProposal>(); val f=mutableListOf<ReconciledFinding>()
        e.groupBy{it.second.definitionKey}.forEach{(k,items)->
            val g=items.groupBy{norm(it.second.value)}; g.values.forEach{grp->val x=grp.first().second;out+=x.copy(evidence=(grp.flatMap{it.second.evidence}+grp.map{it.first.sourceImageUri}).distinct())}
            val conflict=g.size>1&&!items.first().second.multiValued
            f+=ReconciledFinding(k,items.first().second.value,if(conflict)null else norm(items.first().second.value),if(conflict)"CONFLICT" else if(items.size>1)"AGREEMENT" else "UNIQUE",items.map{it.first.sourceImageUri}.distinct(),items.flatMap{it.second.evidence}.distinct(),if(conflict)g.values.map{it.first().second.value}else emptyList())
        }
        return out to f
    }

    private fun reconcileIngs(xs:List<Pair<ProductScanObservation,ProductIdentityInterpretation>>):Pair<List<ProductIngredientProposal>,List<ReconciledFinding>>{
        val e=xs.flatMap{(o,i)->i.draft.ingredientProposals.map{o to it}}
        val out=mutableListOf<ProductIngredientProposal>();val f=mutableListOf<ReconciledFinding>()
        e.groupBy{norm(it.second.ingredientName)}.forEach{(name,items)->
            val sg=items.groupBy{norm(listOfNotNull(it.second.strengthValue,it.second.strengthUnit,it.second.denominatorValue,it.second.denominatorUnit).joinToString("/"))}
            val conflict=sg.size>1
            val x=items.first().second
            out+=if(conflict)x.copy(strengthValue=null,strengthUnit=null,denominatorValue=null,denominatorUnit=null) else x
            f+=ReconciledFinding("ingredient:$name",x.ingredientName,name,if(conflict)"CONFLICT" else if(items.size>1)"AGREEMENT" else "UNIQUE",items.map{it.first.sourceImageUri}.distinct(),items.flatMap{it.second.evidence}.distinct(),if(conflict)sg.values.map{g->g.first().second.let{listOfNotNull(it.strengthValue,it.strengthUnit,it.denominatorValue,it.denominatorUnit).joinToString("/")}}else emptyList())
        }
        return out to f
    }

    private fun norm(v:String)=v.trim().lowercase(Locale.ROOT).replace(Regex("\\s+")," ").replace(Regex("\\s*/\\s*"),"/")
    private fun normId(v:String)=v.filter(Char::isDigit).ifBlank{norm(v)}
    private fun FC.normIdKey()=norm

    }

}
