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
    private val concentration = Regex("""\b\d+(?:[.,]\d+)?\s*%(?:\s*(?:v/v|w/v))?\b|\b\d+(?:[.,]\d+)?\s*(?:mg|g)\s*/\s*(?:mL|L)\b""", RegexOption.IGNORE_CASE)
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
    private fun materials(lines:List<String>)=(after(lines,listOf("Material","Made of","Made from","Composition"))+lines.filter{Regex("""(?i)\b(?:PVC|silicone|latex|polyurethane|polypropylene|polyethylene|stainless steel|cotton|non[- ]woven)\b""").containsMatchIn(it)&&Regex("""(?i)\b(?:material|made|composition)\b""").containsMatchIn(it)}).distinct().map{p("material","TEXT",it,"MaterialSignature",it)}
    private fun intended(lines:List<String>)=after(lines,listOf("Intended use","Purpose","Use","For")).map{p("intended_use","TEXT",it,"PurposeIntendedUseSignature",it)}
    private fun profile(type:ProductType):List<VariableRule>{
        fun d(k:String,t:String="TEXT",m:Boolean=false,e:(List<String>,String)->List<CategoryVariableProposal>)=VariableRule(k,t,m,e)
        return when(type){
            ProductType.MEDICINE->listOf(
                d("generic_name"){l,_->after(l,listOf("Generic name","Generic","INN")).map{p("generic_name","TEXT",it,"GenericNameSignature","GENERIC_CUE")}},
                d("strength","QUANTITY"){l,_->matches(l,strength).map{p("strength","QUANTITY",it,"StrengthSignature",it)}},
                d("route"){l,_->after(l,listOf("Route","Route of administration")).map{p("route","TEXT",it,"RouteExplicitSignature","ROUTE_CUE")}},
                d("prescription_classification"){_,t->Regex("""(?i)\b(?:Prescription Only|Prescription Only Medicine|OTC|Over the Counter|Pharmacy Medicine)\b""").findAll(t).map{p("prescription_classification","TEXT",it.value,"PrescriptionClassificationSignature","REGULATORY_CUE")}.toList()},
                d("therapeutic_category"){l,_->after(l,listOf("Therapeutic class","Therapeutic category")).map{p("therapeutic_category","TEXT",it,"TherapeuticCategorySignature","CLASSIFICATION_CUE")}},
                d("storage_condition"){l,_->storage(l)},
                d("pack_size","QUANTITY"){l,_->packs(l).map{it.copy(definitionKey="pack_size")}}
            )
            ProductType.MEDICAL_CONSUMABLE->listOf(d("material"){l,_->materials(l)},d("size_gauge","DIMENSION"){l,_->dims(l).map{it.copy(definitionKey="size_gauge")}},d("sterility"){l,_->sterility(l)},d("single_use","BOOLEAN"){l,_->single(l)},d("pack_count","QUANTITY"){l,_->packs(l)},d("storage_condition"){l,_->storage(l)})
            ProductType.DIAGNOSTIC->listOf(
                d("test_analyte"){l,_->after(l,listOf("Test for","Detects","Detection of","Analyte","Antigen","Antibody")).map{p("test_analyte","TEXT",it,"AnalyteSignature","TARGET_CUE")}},
                d("specimen_type"){l,_->after(l,listOf("Specimen","Sample","Sample type","Specimen type","For use with")).map{p("specimen_type","TEXT",it,"SpecimenSignature","SPECIMEN_CUE")}},
                d("method"){l,_->after(l,listOf("Method","Assay method","Principle","Test principle")).map{p("method","TEXT",it,"MethodSignature","METHOD_CUE")}},
                d("pack_count","QUANTITY"){l,_->packs(l)},d("storage_condition"){l,_->storage(l)})
            ProductType.WOUND_CARE->listOf(
                d("dressing_type"){l,_->matches(l,Regex("""(?i)\b(?:gauze|adhesive dressing|non-adherent dressing|hydrocolloid|hydrogel|foam dressing|alginate|transparent film|absorbent dressing|wound pad)\b""")).map{p("dressing_type","TEXT",it,"DressingTypeSignature","DRESSING_LEXICON")}},
                d("size","DIMENSION"){l,_->dims(l)},d("adhesive"){l,_->after(l,listOf("Adhesive")).map{p("adhesive","TEXT",it,"AdhesiveSignature","ADHESIVE_CUE")}},d("sterility"){l,_->sterility(l)},d("pack_count","QUANTITY"){l,_->packs(l)},d("material"){l,_->materials(l)})
            ProductType.ANTISEPTIC_DISINFECTANT->listOf(
                d("active_concentration","QUANTITY"){l,_->matches(l,concentration).map{p("active_concentration","QUANTITY",it,"ConcentrationSignature",it)}},
                d("intended_use"){l,_->intended(l)},d("dilution"){l,_->matches(l,Regex("""(?i)\b\d+\s*:\s*\d+\b|\bdilute\s+1\s+in\s+\d+\b""")).map{p("dilution","TEXT",it,"DilutionSignature",it)}},
                d("volume","QUANTITY"){l,_->vols(l,listOf("Volume","Net content","Contents"))},d("storage_condition"){l,_->storage(l)})
            ProductType.PERSONAL_CARE_HYGIENE->listOf(
                d("intended_use"){l,_->intended(l)},d("strength","QUANTITY"){l,_->matches(l,Regex("""(?i)\b(?:\d+(?:[.,]\d+)?\s*%|SPF\s*\d+)\b""")).filter{Regex("""(?i)\b(?:alcohol|antibacterial|SPF)\b""").containsMatchIn(it)}.map{p("strength","QUANTITY",it,"PersonalCareStrengthSignature",it)}},
                d("volume_pack_size","QUANTITY"){l,_->vols(l,listOf("Volume","Net content","Contents","Pack size")).map{it.copy(definitionKey="volume_pack_size")}},
                d("variant"){l,_->matches(l,Regex("""(?i)\b(?:lemon|mint|lavender|aloe vera|sensitive|extra fresh|original|kids|vanilla|chocolate|strawberry|orange|unflavoured|unflavored)\b""")).map{p("variant","TEXT",it,"VariantSignature","VARIANT_LEXICON")}},d("storage_condition"){l,_->storage(l)})
            ProductType.MEDICAL_DEVICE_EQUIPMENT->listOf(
                d("device_type"){l,_->matches(l,Regex("""(?i)\b(?:blood pressure monitor|pulse oximeter|nebulizer|thermometer|infusion pump|wheelchair|stethoscope|suction machine)\b""")).map{p("device_type","TEXT",it,"DeviceTypeSignature","DEVICE_LEXICON")}},
                d("model_catalogue_no"){l,_->after(l,listOf("Model","Model No.","Model Number","REF","Ref.","Cat. No.","Catalogue No.")).map{p("model_catalogue_no","TEXT",it,"ModelReferenceSignature","MODEL_CUE")}},
                d("single_use","BOOLEAN"){l,_->single(l)},d("sterility"){l,_->sterility(l)},d("size_configuration"){l,_->after(l,listOf("Size","Configuration")).map{p("size_configuration","TEXT",it,"DeviceSizeConfigurationSignature","CONFIGURATION_CUE")}})
            ProductType.LABORATORY_SPECIMEN_SUPPLY->listOf(
                d("container_specimen_type"){l,_->after(l,listOf("Specimen","Container","Specimen type","For use with")).map{p("container_specimen_type","TEXT",it,"ContainerSpecimenSignature","CONTAINER_SPECIMEN_CUE")}},
                d("additive_medium"){l,_->matches(l,Regex("""(?i)\b(?:EDTA|sodium citrate|heparin|fluoride|oxalate|transport medium|viral transport medium|gel separator)\b""")).filter{Regex("""(?i)\b(?:tube|container|collection|medium|additive)\b""").containsMatchIn(it)}.map{p("additive_medium","TEXT",it,"AdditiveMediumSignature","LAB_CONTEXT")}},
                d("volume_capacity","QUANTITY"){l,_->vols(l,listOf("Capacity","Volume")).map{it.copy(definitionKey="volume_capacity")}},d("pack_count","QUANTITY"){l,_->packs(l)},d("sterility"){l,_->sterility(l)},d("storage_condition"){l,_->storage(l)})
            ProductType.NUTRITION_THERAPEUTIC_FOOD->listOf(
                d("purpose"){l,_->intended(l)},d("key_nutrients","QUANTITY",true){l,_->matches(l,Regex("""(?i)\b(?:protein|iron|vitamin\s+[A-Za-z]+|calcium|zinc|sodium|potassium|fat|carbohydrate)\s+\d+(?:[.,]\d+)?\s*(?:mg|mcg|g|kcal)\b""")).map{p("key_nutrients","QUANTITY",it,"NutrientSignature","NUTRIENT_PLUS_QUANTITY",true)}},
                d("strength_per_serving","QUANTITY"){l,_->matches(l,Regex("""(?i)\b\d+(?:[.,]\d+)?\s*(?:g|mg|kcal)\s*(?:per|/)\s*(?:serving|sachet|dose)\b""")).map{p("strength_per_serving","QUANTITY",it,"ServingStrengthSignature","SERVING_DENOMINATOR")}},
                d("flavour_variant"){l,_->matches(l,Regex("""(?i)\b(?:vanilla|chocolate|strawberry|orange|unflavoured|unflavored)\b""")).map{p("flavour_variant","TEXT",it,"VariantSignature","FLAVOUR_LEXICON")}},
                d("net_content","QUANTITY"){l,_->vols(l,listOf("Net content","Net weight","Contents")).map{it.copy(definitionKey="net_content")}},d("storage_condition"){l,_->storage(l)})
            )
            ProductType.OTHER_HEALTH_COMMODITY->listOf(d("intended_use"){l,_->intended(l)},d("pack_size","QUANTITY"){l,_->packs(l).map{it.copy(definitionKey="pack_size")}},d("storage_condition"){l,_->storage(l)})
        }
    }

    fun extract(productType: ProductType, ocr: List<OcrResult>): CategoryExtractionResult {
        val lines=ocr.flatMap{r->r.blocks.flatMap{b->b.lines.map{it.text}}.ifEmpty{r.text.lines()}}.map(String::trim).filter(String::isNotBlank).distinct()
        val text=lines.joinToString("\n")
        val variables=profile(productType).flatMap{rule->rule.extractor(lines,text).map{it.copy(definitionKey=rule.key,valueType=rule.type,multiValued=rule.multi)}}
        val ingredients=if(productType==ProductType.MEDICINE) extractIngredients(lines) else emptyList()
        return CategoryExtractionResult(variables,ingredients)
    }

    private fun extractIngredients(lines:List<String>):List<ProductIngredientProposal>{
        val cue=Regex("""(?i)\b(?:active ingredients?|each (?:tablet|capsule|5 mL|dose) contains|contains)\s*[:\-]?\s*(.*)$""")
        val result=mutableListOf<ProductIngredientProposal>()
        for(line in lines){
            val body=cue.find(line)?.groupValues?.get(1)?.trim() ?: continue
            val s=Regex("""(?i)\b(\d+(?:[.,]\d+)?)\s*(mg|mcg|µg|g|kg|IU|mmol)(?:\s*/\s*(\d+(?:[.,]\d+)?)\s*(mL|L|g|kg|mg|mmol))?\b""").find(body)
            val name=body.substringBefore(s?.value?:"").trim().trim(',', ';', ':', '-')
            if(name.isBlank()) continue
            result += ProductIngredientProposal(name,s?.groupValues?.getOrNull(1),s?.groupValues?.getOrNull(2),s?.groupValues?.getOrNull(3),s?.groupValues?.getOrNull(4),listOf(line,"IngredientSignature"))
        }
        return result.distinctBy{"${it.ingredientName.lowercase(Locale.ROOT)}|${it.strengthValue}|${it.denominatorValue}"}
    }
}
