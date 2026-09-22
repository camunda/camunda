package buildlogic

/**
 * Defaults shared by the modules that generate Java sources from OpenAPI specs.
 *
 * Modules still own the parts that differ (generator name, package, template dir, type/import
 * mappings, and spec-specific config options); this only centralizes the literals that are
 * otherwise copy-pasted across them.
 */
object OpenApiDefaults {
  /** Generate models only: no APIs and no supporting files. */
  val MODEL_ONLY_GLOBAL_PROPERTIES: Map<String, String> =
    mapOf("models" to "", "apis" to "false", "supportingFiles" to "false")

  const val HIDE_GENERATION_TIMESTAMP = "true"

  const val SOURCE_FOLDER = "src/main/java"

  const val NON_NULL_MODEL_TYPE_ANNOTATIONS =
    "@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)"
}
