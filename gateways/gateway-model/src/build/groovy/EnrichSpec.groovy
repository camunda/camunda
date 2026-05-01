// gateways/gateway-model/src/build/groovy/EnrichSpec.groovy
//
// Reads the OpenAPI spec, emits an enriched copy with `x-staged-chain` per schema.
// Each chain entry: { name, pascalName, type, nullableAnnotation, isLast, nextStage }.
// The merged chain = parent.required (recursively) + own.required, in spec order.
//
// Type computation handles: primitives, arrays, maps, refs (use schema name).
// Mirrors the `OffsetDateTime → String` typeMapping from gateway-model/pom.xml.
//
// NOTE: The input spec (rest-api.yaml) is a multi-file spec; components/schemas are
// distributed across sibling YAML files. This script scans all *.yaml files in the
// same directory as the input spec and merges their components.schemas sections.

import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.DumperOptions

def args_ = this.args
if (args_.size() != 2) {
  throw new IllegalArgumentException("Usage: EnrichSpec.groovy <inputSpec> <outputSpec>")
}
def inputFile = new File(args_[0])
def outputFile = new File(args_[1])
outputFile.parentFile.mkdirs()

def yaml = new Yaml()

// --- Load, per-file schema names, and merged schemas map ---

// The entry-point spec is a multi-file spec; schemas live in sibling YAML files.
// Merge all components.schemas from every *.yaml in the same directory.
// Also record which schema names came from which file for the write-out step.
def schemas = [:]
def fileSchemaNames = [:]  // Map<File, List<String>>
inputFile.parentFile.listFiles({ f -> f.name.endsWith('.yaml') } as FileFilter)?.sort { it.name }?.each { f ->
  def doc = yaml.load(f.text)
  def fileSchemas = doc?.components?.schemas
  if (fileSchemas instanceof Map) {
    def names = (fileSchemas as Map).keySet() as List
    fileSchemaNames[f] = names
    schemas.putAll(fileSchemas)
  } else {
    fileSchemaNames[f] = []
  }
}

// --- Parent relationships ---

// Build a map of schema-name -> direct parent name (via allOf $ref).
def parentOf = [:]
schemas.each { name, schema ->
  if (schema instanceof Map && schema.allOf instanceof List) {
    schema.allOf.each { ref ->
      if (ref instanceof Map && ref['$ref']) {
        parentOf[name] = ((String) ref['$ref']).tokenize('/').last()
      }
    }
  }
}

// Recursively walk to collect parent chain.
def parentChain
parentChain = { String n ->
  def chain = []
  def cur = parentOf[n]
  while (cur) {
    chain << cur
    cur = parentOf[cur]
  }
  return chain.reverse()  // root-most first
}

// Required list for a schema, in spec order (preserves the order in the YAML's `required:` list).
def ownRequired = { Map schema -> (schema.required ?: []) }

// Merged required = (recursive parent.required, root-most first) + own.required
def mergedRequired = { String name ->
  def out = []
  parentChain(name).each { p -> out.addAll(ownRequired(schemas[p] as Map)) }
  out.addAll(ownRequired(schemas[name] as Map))
  return out
}

println "[EnrichSpec] schemas: ${schemas.size()}, parents found: ${parentOf.size()}"

// --- Type computation ---

// Type-name overrides — must mirror <typeMappings> in gateway-model/pom.xml for required-field types.
// Tested in EnrichSpecTest for parity.
def TYPE_OVERRIDES = [
  'OffsetDateTime': 'String',
]

// Resolve OpenAPI schema fragment to Java type. Walks $refs, primitives, arrays, maps.
def javaType
javaType = { Map fragment ->
  if (fragment == null) return 'Object'
  if (fragment['$ref']) {
    def name = ((String) fragment['$ref']).tokenize('/').last()
    return TYPE_OVERRIDES[name] ?: name
  }
  if (fragment.allOf) {
    // schema with allOf at the property level — common pattern in this spec for typed wrappers.
    // Resolve via the first $ref in allOf.
    def ref = fragment.allOf.find { it['$ref'] }
    if (ref) {
      def name = ((String) ref['$ref']).tokenize('/').last()
      return TYPE_OVERRIDES[name] ?: name
    }
  }
  switch (fragment.type) {
    case 'string':
      if (fragment.format == 'date-time') return TYPE_OVERRIDES['OffsetDateTime'] ?: 'OffsetDateTime'
      return 'String'
    case 'integer':
      return fragment.format == 'int64' ? 'Long' : 'Integer'
    case 'number':
      return fragment.format == 'float' ? 'Float' : 'Double'
    case 'boolean':
      return 'Boolean'
    case 'array':
      return "List<${javaType(fragment.items as Map)}>".toString()
    case 'object':
      if (fragment.additionalProperties instanceof Map) {
        return "Map<String, ${javaType(fragment.additionalProperties as Map)}>".toString()
      }
      return 'Object'
    default:
      return 'Object'
  }
}

def pascalCase = { String s -> s ? s[0].toUpperCase() + s.substring(1) : s }

// Find the property's schema fragment by name. Walks parent chain to find inherited properties.
def findProperty
findProperty = { String schemaName, String propName ->
  def s = schemas[schemaName] as Map
  if (s?.properties?.containsKey(propName)) return s.properties[propName]
  def parent = parentOf[schemaName]
  return parent ? findProperty(parent, propName) : null
}

// --- x-staged-chain injection ---

// Emit x-staged-chain on each schema with required fields.
schemas.each { name, schema ->
  if (!(schema instanceof Map)) return
  def merged = mergedRequired(name)
  if (merged.isEmpty()) return  // zero-required schemas don't get a chain
  def chain = []
  merged.eachWithIndex { propName, idx ->
    def propSchema = findProperty(name, propName)
    if (propSchema == null) {
      throw new IllegalStateException("Required property '${propName}' on schema '${name}' has no schema — spec error?")
    }
    def isLast = (idx == merged.size() - 1)
    def nextProp = isLast ? null : merged[idx + 1]
    def nullable = (propSchema instanceof Map) && propSchema.nullable == true
    chain << [
      name             : propName,
      pascalName       : pascalCase(propName),
      type             : javaType(propSchema as Map),
      nullableAnnotation: nullable ? '@org.jspecify.annotations.Nullable ' : '',
      isLast           : isLast,
      nextStage        : nextProp ? pascalCase(nextProp) : null,
    ]
  }
  schema['x-staged-chain'] = chain
}

// --- Multi-file YAML write-out ---

// Serialize YAML with block style + pretty.
def dumpOpts = new DumperOptions().with {
  defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
  prettyFlow = true
  it
}

// Write each input file's enriched copy to the output dir.
def outputDir = outputFile.parentFile
fileSchemaNames.each { f, names ->
  def doc = yaml.load(f.text)
  if (doc?.components?.schemas instanceof Map && !names.isEmpty()) {
    // Replace each schema in this file with its enriched version from the merged map.
    def enriched = [:]
    names.each { schemaName ->
      enriched[schemaName] = schemas[schemaName]
    }
    doc.components.schemas = enriched
  }
  def outFile = new File(outputDir, f.name)
  outFile.text = new Yaml(dumpOpts).dumpAsMap(doc)
}
println "[EnrichSpec] wrote ${fileSchemaNames.size()} enriched files to ${outputDir}"
