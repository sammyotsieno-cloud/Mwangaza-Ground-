package org.mwangaza.app.scanner.interpretation

import core.domain.model.ProductType
import org.mwangaza.app.scanner.OcrResult
import java.util.Locale

data class CategoryVariableProposal(
    val definitionKey: String,
    val valueType: String,
    val value: String,
    val normalizedValue: String? = null,
    val provenance: String = "OCR_STRUCTURED",
    val evidence: List<String> = emptyList(),
    val ruleName: String? = null,
    val multiValued: Boolean = false
)

/** Presentation-neutral metadata exposed to the review layer from the authoritative category profile. */
data class CategoryVariableDefinition(
    val definitionKey: String,
    val valueType: String,
    val multiValued: Boolean
)

data class ProductIngredientProposal(
    val ingredientName: String,
    val strengthValue: String? = null,
    val strengthUnit: String? = null,
    val denominatorValue: String? = null,
    val denominatorUnit: String? = null,
    val evidence: List<String> = emptyList()
)

data class CategoryExtractionResult(
    val variables: List<CategoryVariableProposal> = emptyList(),
    val ingredients: List<ProductIngredientProposal> = emptyList()
)

private data class VariableRule(
    val key: String,
    val type: String,
    val multi: Boolean = false,
    val extractor: (List<String>, String) -> List<CategoryVariableProposal>
)

object CategoryExtractionProfiles {
    private val strength = Regex("""\b\d+(?:[.,]\d+)?\s*(?:mg|mcg|µg|g|kg|mL|L|mmol|IU|%)\b(?:\s*/\s*\d+(?:[.,]\d+)?\s*(?:mL|L|g|kg|mg|mmol)\b)?""", RegexOption.IGNORE_CASE)
    private val concentration = Regex("""\b\d+(?:[.,]\d+)?\s*%(?:\s*(?:v/v|w/v))?(?=\s|$|[^A-Za-z0-9_])|\b\d+(?:[.,]\d+)?\s*(?:mg|g)\s*/\s*(?:mL|L)\b""", RegexOption.IGNORE_CASE)
    private val dimension = Regex("""\b\d+(?:[.,]\d+)?\s*(?:mm|cm|in|")\s*(?:x|×)\s*\d+(?:[.,]\d+)?\s*(?:mm|cm|in|")\b|\b\d+(?:[.,]\d+)?\s*G\b""", RegexOption.IGNORE_CASE)
    private val storageCue = Regex("""(?i)\b(?:store|storage|protect from light|do not freeze|keep dry|keep refrigerated)\b""")
    private val storageTemp = Regex("""(?i)\b(?:below|under|at)\s*\d+(?:[.,]\d+)?\s*°?C(?:\s*(?:-|to)\s*\d+(?:[.,]\d+)?\s*°?C)?\b""")
    private val sterile = Regex("""(?i)\b(?:sterile|non[- ]sterile|sterilized|EO sterilized|gamma sterilized|steam sterilized)\b""")
    private val singleUse = Regex("""(?i)\b(?:single[- ]use|do not reuse|disposable|for one use only|reusable|multi[- ]use)\b""")
    private val pack = Regex("""(?i)\b(?:pack|box|carton|case)\s+of\s+\d+(?:\s+[A-Za-z]+)?\b|\b\d+\s+(?:pieces|pcs|tests|strips|cassettes|cartridges|vials|sachets|tablets|capsules|gloves|syringes|swabs)\b""")
    private val volume = Regex("""(?i)\b\d+(?:[.,]\d+)?\s*(?:mL|L)\b""")

    private fun p(key:String,type:String,value:String,rule:String,evidence:String,multi:Boolean=false)=CategoryVariableProposal(
        key,type,value,value.trim().lowercase(Locale.ROOT), "OCR_STRUCTURED", listOf(evidence), rule,multi)

    private fun after(lines:List<String>, cues:List<String>):List<String>{
        val cue=cues.joinToString("|"){Regex.escape(it)}
        val r=Regex("""(?i)^(?:$cue)\s*[:\-]?\s*(.+)$""")
        return lines.mapNotNull{r.find(it)?.groupValues?.get(1)?.trim()}.filter{it.isNotBlank()}
    }
    private fun matches(lines:List<String>, r:Regex)=lines.flatMap{line->r.findAll(line).map{it.value}.toList()}.distinct()
    private fun storage(lines:List<String>)=lines.filter{storageCue.containsMatchIn(it)||storageTemp.containsMatchIn(it)}.map{p("storage_condition","TEXT",it,"TemperatureStorageSignature",it)}
    private fun sterility(lines:List<String>)=matches(lines,sterile).map{p("sterility","TEXT",it,"SterilitySignature",it)}
    private fun single(lines:List<String>)=matches(lines,singleUse).map{p("single_use","BOOLEAN",it,"SingleUseSignature",it)}
    private fun packs(lines:List<String>)=matches(lines,pack).map{p("pack_count","QUANTITY",it,"PackCountSignature",it)}
    private fun dims(lines:List<String>)=matches(lines,dimension).map{p("size","DIMENSION",it,"DimensionSignature",it)}
    private fun vols(lines:List<String>,cues:List<String>)=(after(lines,cues)+lines.filter{volume.containsMatchIn(it)&&Regex("""(?i)\b(?:volume|net|content|capacity|bottle|container|pack)\b""").containsMatchIn(it)}.flatMap{volume.findAll(it).map{m->m.value}}).distinct().map{p("volume","QUANTITY",it,"VolumeSignature",it)}
    private val materialLexicon = Regex("""(?i)\b(?:PVC|silicone|latex|polyurethane|polypropylene|polyethylene|stainless steel|cotton|non[- ]woven)\b""")
    private val materialContext = Regex("""(?i)\b(?:material|made|composition)\b""")
    private fun materials(lines:List<String>)=(after(lines,listOf("Material","Made of","Made from","Composition"))+lines.filter{materialLexicon.containsMatchIn(it)&&materialContext.containsMatchIn(it)}.flatMap{line->materialLexicon.findAll(line).map{it.value}}).distinct().map{p("material","TEXT",it,"MaterialSignature",it)}
    private fun intended(lines:List<String>)=after(lines,listOf("Intended use","Purpose","Use","For")).map{p("intended_use","TEXT",it,"PurposeIntendedUseSignature",it)}
    private fun profile(type: ProductType): List<VariableRule> {
        fun d(
            k: String,
            t: String = "TEXT",
            m: Boolean = false,
            e: (List<String>, String) -> List<CategoryVariableProposal>
        ): VariableRule = VariableRule(k, t, m, e)

        return when (type) {
            ProductType.MEDICINE -> listOf(
                d("generic_name") { lines, _ ->
                    after(lines, listOf("Generic name", "Generic", "INN"))
                        .map { p("generic_name", "TEXT", it, "GenericNameSignature", "GENERIC_CUE") }
                },
                d("strength", "QUANTITY") { lines, _ ->
                    matches(lines, strength)
                        .map { p("strength", "QUANTITY", it, "StrengthSignature", it) }
                },
                d("route") { lines, _ ->
                    after(lines, listOf("Route", "Route of administration"))
                        .map { p("route", "TEXT", it, "RouteExplicitSignature", "ROUTE_CUE") }
                },
                d("prescription_classification") { _, text ->
                    Regex("""(?i)\b(?:Prescription Only|Prescription Only Medicine|OTC|Over the Counter|Pharmacy Medicine)\b""")
                        .findAll(text)
                        .map {
                            p(
                                "prescription_classification",
                                "TEXT",
                                it.value,
                                "PrescriptionClassificationSignature",
                                "REGULATORY_CUE"
                            )
                        }
                        .toList()
                },
                d("therapeutic_category") { lines, _ ->
                    after(lines, listOf("Therapeutic class", "Therapeutic category"))
                        .map {
                            p(
                                "therapeutic_category",
                                "TEXT",
                                it,
                                "TherapeuticCategorySignature",
                                "CLASSIFICATION_CUE"
                            )
                        }
                },
                d("storage_condition") { lines, _ -> storage(lines) },
                d("pack_size", "QUANTITY") { lines, _ ->
                    packs(lines).map { it.copy(definitionKey = "pack_size") }
                }
            )

            ProductType.MEDICAL_CONSUMABLE -> listOf(
                d("material") { lines, _ ->
                    (materials(lines) + lines
                        .filter { materialLexicon.containsMatchIn(it) }
                        .flatMap { line -> materialLexicon.findAll(line).map { it.value } })
                        .distinct()
                        .map { p("material", "TEXT", it, "MedicalConsumableMaterialLexicon", it) }
                },
                d("size_gauge", "DIMENSION") { lines, _ ->
                    dims(lines).map { it.copy(definitionKey = "size_gauge") }
                },
                d("sterility") { lines, _ -> sterility(lines) },
                d("single_use", "BOOLEAN") { lines, _ -> single(lines) },
                d("pack_count", "QUANTITY") { lines, _ -> packs(lines) },
                d("storage_condition") { lines, _ -> storage(lines) }
            )

            ProductType.DIAGNOSTIC -> listOf(
                d("test_analyte") { lines, _ ->
                    after(lines, listOf("Test for", "Detects", "Detection of", "Analyte", "Antigen", "Antibody"))
                        .map { p("test_analyte", "TEXT", it, "AnalyteSignature", "TARGET_CUE") }
                },
                d("specimen_type") { lines, _ ->
                    (after(lines, listOf("Specimen", "Sample", "Sample type", "Specimen type", "For use with")) +
                        lines.filter { Regex("""(?i)\b.+\bspecimen\b""").matches(it) })
                        .distinct()
                        .map { p("specimen_type", "TEXT", it, "SpecimenSignature", "SPECIMEN_CUE") }
                },
                d("method") { lines, _ ->
                    after(lines, listOf("Method", "Assay method", "Principle", "Test principle"))
                        .map { p("method", "TEXT", it, "MethodSignature", "METHOD_CUE") }
                },
                d("pack_count", "QUANTITY") { lines, _ -> packs(lines) },
                d("storage_condition") { lines, _ -> storage(lines) }
            )

            ProductType.WOUND_CARE -> listOf(
                d("dressing_type") { lines, _ ->
                    matches(
                        lines,
                        Regex("""(?i)\b(?:gauze|adhesive dressing|non-adherent dressing|hydrocolloid|hydrogel|foam dressing|alginate|transparent film|absorbent dressing|wound pad)\b""")
                    ).map {
                        p(
                            "dressing_type",
                            "TEXT",
                            it,
                            "DressingTypeSignature",
                            "DRESSING_LEXICON"
                        )
                    }
                },
                d("size", "DIMENSION") { lines, _ -> dims(lines) },
                d("adhesive") { lines, _ ->
                    after(lines, listOf("Adhesive"))
                        .map { p("adhesive", "TEXT", it, "AdhesiveSignature", "ADHESIVE_CUE") }
                },
                d("sterility") { lines, _ -> sterility(lines) },
                d("pack_count", "QUANTITY") { lines, _ -> packs(lines) },
                d("material") { lines, _ -> materials(lines) }
            )

            ProductType.ANTISEPTIC_DISINFECTANT -> listOf(
                d("active_concentration", "QUANTITY") { lines, _ ->
                    matches(lines, concentration)
                        .map { p("active_concentration", "QUANTITY", it, "ConcentrationSignature", it) }
                },
                d("intended_use") { lines, _ -> intended(lines) },
                d("dilution") { lines, _ ->
                    matches(
                        lines,
                        Regex("""(?i)\b\d+\s*:\s*\d+\b|\bdilute\s+1\s+in\s+\d+\b""")
                    ).map { p("dilution", "TEXT", it, "DilutionSignature", it) }
                },
                d("volume", "QUANTITY") { lines, _ ->
                    vols(lines, listOf("Volume", "Net content", "Contents"))
                },
                d("storage_condition") { lines, _ -> storage(lines) }
            )

            ProductType.PERSONAL_CARE_HYGIENE -> listOf(
                d("intended_use") { lines, _ -> intended(lines) },
                d("strength", "QUANTITY") { lines, _ ->
                    matches(
                        lines,
                        Regex("""(?i)\b(?:\d+(?:[.,]\d+)?\s*%|SPF\s*\d+)\b""")
                    )
                        .filter {
                            Regex("""(?i)\b(?:alcohol|antibacterial|SPF)\b""")
                                .containsMatchIn(it)
                        }
                        .map { p("strength", "QUANTITY", it, "PersonalCareStrengthSignature", it) }
                },
                d("volume_pack_size", "QUANTITY") { lines, _ ->
                    vols(
                        lines,
                        listOf("Volume", "Net content", "Contents", "Pack size")
                    ).map { it.copy(definitionKey = "volume_pack_size") }
                },
                d("variant") { lines, _ ->
                    matches(
                        lines,
                        Regex("""(?i)\b(?:lemon|mint|lavender|aloe vera|sensitive|extra fresh|original|kids|vanilla|chocolate|strawberry|orange|unflavoured|unflavored)\b""")
                    ).map { p("variant", "TEXT", it, "VariantSignature", "VARIANT_LEXICON") }
                },
                d("storage_condition") { lines, _ -> storage(lines) }
            )

            ProductType.MEDICAL_DEVICE_EQUIPMENT -> listOf(
                d("device_type") { lines, _ ->
                    matches(
                        lines,
                        Regex("""(?i)\b(?:blood pressure monitor|pulse oximeter|nebulizer|thermometer|infusion pump|wheelchair|stethoscope|suction machine)\b""")
                    ).map { p("device_type", "TEXT", it, "DeviceTypeSignature", "DEVICE_LEXICON") }
                },
                d("model_catalogue_no") { lines, _ ->
                    after(
                        lines,
                        listOf(
                            "Model",
                            "Model No.",
                            "Model Number",
                            "REF",
                            "Ref.",
                            "Cat. No.",
                            "Catalogue No."
                        )
                    ).map {
                        p(
                            "model_catalogue_no",
                            "TEXT",
                            it,
                            "ModelReferenceSignature",
                            "MODEL_CUE"
                        )
                    }
                },
                d("single_use", "BOOLEAN") { lines, _ -> single(lines) },
                d("sterility") { lines, _ -> sterility(lines) },
                d("size_configuration") { lines, _ ->
                    after(lines, listOf("Size", "Configuration"))
                        .map {
                            p(
                                "size_configuration",
                                "TEXT",
                                it,
                                "DeviceSizeConfigurationSignature",
                                "CONFIGURATION_CUE"
                            )
                        }
                }
            )

            ProductType.LABORATORY_SPECIMEN_SUPPLY -> listOf(
                d("container_specimen_type") { lines, _ ->
                    after(
                        lines,
                        listOf("Specimen", "Container", "Specimen type", "For use with")
                    ).map {
                        p(
                            "container_specimen_type",
                            "TEXT",
                            it,
                            "ContainerSpecimenSignature",
                            "CONTAINER_SPECIMEN_CUE"
                        )
                    }
                },
                d("additive_medium") { lines, _ ->
                    val additiveLexicon = Regex("""(?i)\b(?:EDTA|sodium citrate|heparin|fluoride|oxalate|transport medium|viral transport medium|gel separator)\b""")
                    val labContext = Regex("""(?i)\b(?:tube|container|collection|medium|additive)\b""")
                    lines.flatMap { line ->
                        additiveLexicon.findAll(line)
                            .filter { labContext.containsMatchIn(line) }
                            .map { match ->
                                p(
                                    "additive_medium",
                                    "TEXT",
                                    match.value,
                                    "AdditiveMediumSignature",
                                    line
                                )
                            }
                    }.distinctBy { it.value.lowercase(Locale.ROOT) }
                },
                d("volume_capacity", "QUANTITY") { lines, _ ->
                    vols(lines, listOf("Capacity", "Volume"))
                        .map { it.copy(definitionKey = "volume_capacity") }
                },
                d("pack_count", "QUANTITY") { lines, _ -> packs(lines) },
                d("sterility") { lines, _ -> sterility(lines) },
                d("storage_condition") { lines, _ -> storage(lines) }
            )

            ProductType.NUTRITION_THERAPEUTIC_FOOD -> listOf(
                d("purpose") { lines, _ -> intended(lines) },
                d("key_nutrients", "QUANTITY", true) { lines, _ ->
                    matches(
                        lines,
                        Regex("""(?i)\b(?:protein|iron|vitamin\s+[A-Za-z]+|calcium|zinc|sodium|potassium|fat|carbohydrate)\s+\d+(?:[.,]\d+)?\s*(?:mg|mcg|g|kcal)\b""")
                    ).map {
                        p(
                            "key_nutrients",
                            "QUANTITY",
                            it,
                            "NutrientSignature",
                            "NUTRIENT_PLUS_QUANTITY",
                            true
                        )
                    }
                },
                d("strength_per_serving", "QUANTITY") { lines, _ ->
                    matches(
                        lines,
                        Regex("""(?i)\b\d+(?:[.,]\d+)?\s*(?:g|mg|kcal)\s*(?:per|/)\s*(?:serving|sachet|dose)\b""")
                    ).map {
                        p(
                            "strength_per_serving",
                            "QUANTITY",
                            it,
                            "ServingStrengthSignature",
                            "SERVING_DENOMINATOR"
                        )
                    }
                },
                d("flavour_variant") { lines, _ ->
                    matches(
                        lines,
                        Regex("""(?i)\b(?:vanilla|chocolate|strawberry|orange|unflavoured|unflavored)\b""")
                    ).map { p("flavour_variant", "TEXT", it, "VariantSignature", "FLAVOUR_LEXICON") }
                },
                d("net_content", "QUANTITY") { lines, _ ->
                    vols(lines, listOf("Net content", "Net weight", "Contents"))
                        .map { it.copy(definitionKey = "net_content") }
                },
                d("storage_condition") { lines, _ -> storage(lines) }
            )

            ProductType.OTHER_HEALTH_COMMODITY -> listOf(
                d("intended_use") { lines, _ -> intended(lines) },
                d("pack_size", "QUANTITY") { lines, _ ->
                    packs(lines).map { it.copy(definitionKey = "pack_size") }
                },
                d("storage_condition") { lines, _ -> storage(lines) }
            )
        }
    }

    /** Returns the exact variable contract for a product type without exposing extraction internals. */
    fun definitions(productType: ProductType): List<CategoryVariableDefinition> =
        profile(productType).map { rule ->
            CategoryVariableDefinition(
                definitionKey = rule.key,
                valueType = rule.type,
                multiValued = rule.multi
            )
        }

    fun extract(productType: ProductType, ocr: List<OcrResult>): CategoryExtractionResult {
        val lines=ocr.flatMap{r->r.blocks.flatMap{b->b.lines.map{it.text}}.ifEmpty{r.text.lines()}}.map(String::trim).filter(String::isNotBlank).distinct()
        val text=lines.joinToString("\n")
        val variables=profile(productType).flatMap{rule->rule.extractor(lines,text).map{it.copy(definitionKey=rule.key,valueType=rule.type,multiValued=rule.multi)}}
        val ingredients=if(productType==ProductType.MEDICINE) extractIngredients(lines) else emptyList()
        return CategoryExtractionResult(variables,ingredients)
    }

    private fun extractIngredients(lines:List<String>):List<ProductIngredientProposal>{
        val cue=Regex("""(?i)\b(?:active ingredients?|each\s+(\d+(?:[.,]\d+)?)\s*(mL|L|g|kg|mg|mmol|tablet|capsule|dose)\s+contains|contains)\s*[:\-]?\s*(.*)$""")
        val result=mutableListOf<ProductIngredientProposal>()
        var denominatorValue:String? = null
        var denominatorUnit:String? = null
        for(line in lines){
            val match=cue.find(line) ?: continue
            val body=match.groupValues.getOrNull(3)?.trim().orEmpty()
            if(match.groupValues.getOrNull(1)?.isNotBlank() == true){
                denominatorValue=match.groupValues[1]
                denominatorUnit=match.groupValues[2]
            }
            val s=Regex("""(?i)\b(\d+(?:[.,]\d+)?)\s*(mg|mcg|µg|g|kg|IU|mmol)(?:\s*/\s*(\d+(?:[.,]\d+)?)\s*(mL|L|g|kg|mg|mmol))?\b""").find(body)
            val name=body.substringBefore(s?.value?:"").trim().trim(',', ';', ':', '-')
            if(name.isBlank()) continue
            result += ProductIngredientProposal(
                name,
                s?.groupValues?.getOrNull(1),
                s?.groupValues?.getOrNull(2),
                s?.groupValues?.getOrNull(3) ?: denominatorValue,
                s?.groupValues?.getOrNull(4) ?: denominatorUnit,
                listOf(line,"IngredientSignature")
            )
        }
        return result.distinctBy{"${it.ingredientName.lowercase(Locale.ROOT)}|${it.strengthValue}|${it.denominatorValue}"}
    }
}
