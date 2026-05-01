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
// This captures ALL allOf $ref relationships regardless of whether they produce Java extends or
// flat inlining — used for property lookup (findProperty) and required field inheritance.
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

// Build a separate map for JAVA inheritance relationships (schemas where the generator will
// produce a Java "extends" clause). The openapi-generator with REF_AS_PARENT_IN_ALLOF=true
// emits Java inheritance when a schema has allOf with a $ref AND the schema has own properties
// (either at top-level OR as inline allOf objects). When allOf contains ONLY a $ref and no
// own properties, the generator inlines the parent's properties as a flat model (no extends).
// This map is used for x-needs-child-ctor and x-all-ctor-args logic.
def javaParentOf = [:]
schemas.each { name, schema ->
  if (!(schema instanceof Map)) return
  def sm = schema as Map
  if (!(sm.allOf instanceof List)) return
  def allOfList = sm.allOf as List
  // Must have at least one $ref entry in allOf.
  if (!allOfList.any { it instanceof Map && it['$ref'] }) return
  // Check if schema has own properties (top-level or inline allOf object entries).
  def hasOwnProps = (sm.get('properties') instanceof Map) && !(sm.get('properties') as Map).isEmpty()
  if (!hasOwnProps) {
    hasOwnProps = allOfList.any { entry ->
      if (!(entry instanceof Map) || (entry as Map).containsKey('$ref')) return false
      def ep = (entry as Map).get('properties')
      return (ep instanceof Map) && !(ep as Map).isEmpty()
    }
  }
  if (hasOwnProps) {
    allOfList.each { ref ->
      if (ref instanceof Map && ref['$ref']) {
        javaParentOf[name] = ((String) ref['$ref']).tokenize('/').last()
      }
    }
  }
  // Schemas with allOf $ref but NO own properties → inlined (no Java extends).
}

// Recursively walk to collect parent chain (via allOf $ref, including flat-inlining schemas).
// Used for property lookup and required field inheritance.
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

// Recursively walk to collect Java parent chain (via javaParentOf — only schemas that produce
// Java extends relationships). Used for x-needs-fallback-ctor and x-needs-child-ctor logic.
def javaParentChain
javaParentChain = { String n ->
  def chain = []
  def cur = javaParentOf[n]
  while (cur) {
    chain << cur
    cur = javaParentOf[cur]
  }
  return chain.reverse()  // root-most first
}

// Required list for a schema, in spec order (preserves the order in the YAML's `required:` list).
// Also checks inside allOf inline objects (non-$ref entries) for nested `required` lists,
// which is a common pattern when schemas use allOf with inline object entries instead of
// top-level required.
def ownRequired = { Map schema ->
  def result = new LinkedHashSet<String>()
  // Top-level required list.
  if (schema.required instanceof List) result.addAll(schema.required as List)
  // Required inside allOf inline objects (non-$ref entries).
  if (schema.allOf instanceof List) {
    (schema.allOf as List).each { entry ->
      if (entry instanceof Map && !(entry as Map).containsKey('$ref')) {
        def nested = (entry as Map).get('required')
        if (nested instanceof List) result.addAll(nested as List)
      }
    }
  }
  return result.toList()
}

// Merged required = (recursive parent.required, root-most first) + own.required, deduplicated.
// Deduplication preserves first occurrence order. This prevents duplicate interface names when
// a child schema re-declares a required field that the parent already declares as required.
def mergedRequired = { String name ->
  def out = []
  def seen = new LinkedHashSet<String>()
  parentChain(name).each { p -> ownRequired(schemas[p] as Map).each { f -> if (seen.add(f)) out << f } }
  ownRequired(schemas[name] as Map).each { f -> if (seen.add(f)) out << f }
  return out
}

println "[EnrichSpec] schemas: ${schemas.size()}, parents found: ${parentOf.size()}"

// --- Type computation ---

// Type-name overrides — must mirror <typeMappings> in gateway-model/pom.xml for required-field types.
// Tested in EnrichSpecTest for parity. These match the ADVANCED execution's typeMappings.
def TYPE_OVERRIDES = [
  'OffsetDateTime': 'String',
  // Filter property wrapper types mapped to their supertype equivalents.
  'AuditLogEntityKeyFilterProperty'                             : 'BasicStringFilterProperty',
  'AuditLogKeyFilterProperty'                                   : 'BasicStringFilterProperty',
  'DecisionDefinitionKeyFilterProperty'                         : 'BasicStringFilterProperty',
  'DecisionEvaluationInstanceKeyFilterProperty'                 : 'BasicStringFilterProperty',
  'DecisionEvaluationKeyFilterProperty'                         : 'BasicStringFilterProperty',
  'DecisionRequirementsKeyFilterProperty'                       : 'BasicStringFilterProperty',
  'DeploymentKeyFilterProperty'                                 : 'BasicStringFilterProperty',
  'ElementIdFilterProperty'                                     : 'StringFilterProperty',
  'ElementInstanceKeyFilterProperty'                            : 'BasicStringFilterProperty',
  'FormKeyFilterProperty'                                       : 'BasicStringFilterProperty',
  'JobKeyFilterProperty'                                        : 'BasicStringFilterProperty',
  'MessageSubscriptionKeyFilterProperty'                        : 'BasicStringFilterProperty',
  'ProcessDefinitionIdFilterProperty'                           : 'StringFilterProperty',
  'ProcessDefinitionKeyFilterProperty'                          : 'BasicStringFilterProperty',
  'ProcessInstanceKeyFilterProperty'                            : 'BasicStringFilterProperty',
  'ResourceKeyFilterProperty'                                   : 'BasicStringFilterProperty',
  'ScopeKeyFilterProperty'                                      : 'BasicStringFilterProperty',
  'VariableKeyFilterProperty'                                   : 'BasicStringFilterProperty',
  // Key identifier schemas that map to String in the advanced generator.
  'ProcessInstanceModificationActivateInstructionAncestorElementInstanceKey': 'String',
  'ResourceKey'                                                 : 'String',
  'ScopeKey'                                                    : 'String',
  'ElementInstanceKey'                                          : 'String',
]

// Type-name overrides for the SIMPLE execution — mirrors the simple execution's <typeMappings>
// in gateway-model/pom.xml. The simple generator maps many filter-property wrapper types directly
// to Java primitives or to simpler model classes. This map is used when computing
// x-simple-staged-chain and x-simple-optional-own-vars so that staged-builder types in the
// simple template match what the simple generator actually generates for each field.
def SIMPLE_TYPE_OVERRIDES = [
  'OffsetDateTime': 'String',
  // Filter property wrapper types — all flattened to String in simple.
  'AuditLogEntityKeyFilterProperty'                             : 'String',
  'AuditLogKeyFilterProperty'                                   : 'String',
  'BasicStringFilterProperty'                                   : 'String',
  'DecisionDefinitionKeyFilterProperty'                         : 'String',
  'DecisionEvaluationInstanceKeyFilterProperty'                 : 'String',
  'DecisionEvaluationKeyFilterProperty'                         : 'String',
  'DecisionRequirementsKeyFilterProperty'                       : 'String',
  'DeploymentKeyFilterProperty'                                 : 'String',
  'ElementIdFilterProperty'                                     : 'String',
  'ElementInstanceKeyFilterProperty'                            : 'String',
  'FormKeyFilterProperty'                                       : 'String',
  'IntegerFilterProperty'                                       : 'Integer',
  'JobKeyFilterProperty'                                        : 'String',
  'MessageSubscriptionKeyFilterProperty'                        : 'String',
  'ProcessDefinitionIdFilterProperty'                           : 'String',
  'ProcessDefinitionKeyFilterProperty'                          : 'String',
  'ProcessInstanceKeyFilterProperty'                            : 'String',
  'ResourceKeyFilterProperty'                                   : 'String',
  'ScopeKeyFilterProperty'                                      : 'String',
  'StringFilterProperty'                                        : 'String',
  'VariableKeyFilterProperty'                                   : 'String',
  // DateTimeFilterProperty maps to the custom SimpleDateTimeFilterProperty class.
  'DateTimeFilterProperty'                                      : 'SimpleDateTimeFilterProperty',
  // Key identifier schemas that map to String in both generators.
  'ProcessInstanceModificationActivateInstructionAncestorElementInstanceKey': 'String',
  'ResourceKey'                                                 : 'String',
  'ScopeKey'                                                    : 'String',
  'ElementInstanceKey'                                          : 'String',
  // Enum filter properties — mapped to shared enum types in both generators.
  'AuditLogActorTypeFilterProperty'                             : 'io.camunda.gateway.protocol.model.AuditLogActorTypeEnum',
  'AuditLogResultFilterProperty'                                : 'io.camunda.gateway.protocol.model.AuditLogResultEnum',
  'BatchOperationItemStateFilterProperty'                       : 'io.camunda.gateway.protocol.model.BatchOperationItemStateEnum',
  'BatchOperationStateFilterProperty'                           : 'io.camunda.gateway.protocol.model.BatchOperationStateEnum',
  'BatchOperationTypeFilterProperty'                            : 'io.camunda.gateway.protocol.model.BatchOperationTypeEnum',
  'CategoryFilterProperty'                                      : 'io.camunda.gateway.protocol.model.AuditLogCategoryEnum',
  'ClusterVariableScopeFilterProperty'                          : 'io.camunda.gateway.protocol.model.ClusterVariableScopeEnum',
  'DecisionInstanceStateFilterProperty'                         : 'io.camunda.gateway.protocol.model.DecisionInstanceStateEnum',
  'EntityTypeFilterProperty'                                    : 'io.camunda.gateway.protocol.model.AuditLogEntityTypeEnum',
  'ElementInstanceStateFilterProperty'                          : 'io.camunda.gateway.protocol.model.ElementInstanceStateEnum',
  'IncidentErrorTypeFilterProperty'                             : 'io.camunda.gateway.protocol.model.IncidentErrorTypeEnum',
  'IncidentStateFilterProperty'                                 : 'io.camunda.gateway.protocol.model.IncidentStateEnum',
  'JobKindFilterProperty'                                       : 'io.camunda.gateway.protocol.model.JobKindEnum',
  'JobListenerEventTypeFilterProperty'                          : 'io.camunda.gateway.protocol.model.JobListenerEventTypeEnum',
  'JobStateFilterProperty'                                      : 'io.camunda.gateway.protocol.model.JobStateEnum',
  'MessageSubscriptionStateFilterProperty'                      : 'io.camunda.gateway.protocol.model.MessageSubscriptionStateEnum',
  'MessageSubscriptionTypeFilterProperty'                       : 'io.camunda.gateway.protocol.model.MessageSubscriptionTypeEnum',
  'ProcessInstanceStateFilterProperty'                          : 'io.camunda.gateway.protocol.model.ProcessInstanceStateEnum',
  'OperationTypeFilterProperty'                                 : 'io.camunda.gateway.protocol.model.AuditLogOperationTypeEnum',
  'UserTaskStateFilterProperty'                                 : 'io.camunda.gateway.protocol.model.UserTaskStateEnum',
  // Polymorphic union types that the simple generator collapses to a single concrete variant.
  'ProcessInstanceCreationRuntimeInstruction'                   : 'ProcessInstanceCreationTerminateInstruction',
]

// Resolve OpenAPI schema fragment to Java type. Walks $refs, primitives, arrays, maps.
// For $ref types: if the referenced schema is a top-level named schema (has allOf, enum, or
// is type: object), it generates its own Java class — return the schema name. If the referenced
// schema is a simple primitive wrapper (type: string/integer/number/boolean with no enum),
// resolve to the Java primitive type. This mirrors openapi-generator's behaviour.
def javaType
javaType = { Map fragment ->
  if (fragment == null) return 'Object'
  if (fragment['$ref']) {
    def refName = ((String) fragment['$ref']).tokenize('/').last()
    if (TYPE_OVERRIDES.containsKey(refName)) return TYPE_OVERRIDES[refName]
    // Recursively resolve the referenced schema to handle identifier types.
    def refSchema = schemas[refName]
    if (refSchema instanceof Map) {
      def rs = refSchema as Map
      // Schemas with enum values generate a named Java enum class — keep the schema name.
      if (rs.enum != null) return refName
      // Object schemas generate a named Java class — keep the schema name.
      if (rs.type == 'object') return refName
      // Schemas with allOf but no primitive type generate a named Java class — keep the schema name.
      // Exception: schemas with allOf AND a primitive type (e.g. string keys with allOf:LongKey)
      // resolve to the primitive type via the switch case below, not to their schema name.
      if (rs.allOf != null && rs.type == null) return refName
      def resolved = javaType(rs)
      // For primitive types (string, int, etc.) without enum, resolve to the Java primitive.
      if (resolved != 'Object') return resolved
    }
    return refName
  }
  if (fragment.allOf) {
    // schema with allOf at the property level — common pattern in this spec for typed wrappers.
    // Resolve via the first $ref in allOf.
    def ref = fragment.allOf.find { it['$ref'] }
    if (ref) {
      def refName = ((String) ref['$ref']).tokenize('/').last()
      if (TYPE_OVERRIDES.containsKey(refName)) return TYPE_OVERRIDES[refName]
      def refSchema = schemas[refName]
      if (refSchema instanceof Map) {
        def rs = refSchema as Map
        if (rs.enum != null) return refName
        if (rs.type == 'object') return refName
        if (rs.allOf != null && rs.type == null) return refName
        def resolved = javaType(rs)
        if (resolved != 'Object') return resolved
      }
      return refName
    }
  }
  if (fragment.oneOf) {
    // oneOf at the property level — resolve via the first $ref in oneOf.
    def ref = fragment.oneOf.find { it['$ref'] }
    if (ref) {
      def refName = ((String) ref['$ref']).tokenize('/').last()
      if (TYPE_OVERRIDES.containsKey(refName)) return TYPE_OVERRIDES[refName]
      def refSchema = schemas[refName]
      if (refSchema instanceof Map) {
        def rs = refSchema as Map
        if (rs.enum != null) return refName
        if (rs.type == 'object') return refName
        if (rs.allOf != null && rs.type == null) return refName
        def resolved = javaType(rs)
        if (resolved != 'Object') return resolved
      }
      return refName
    }
  }
  switch (fragment.type) {
    case 'string':
      if (fragment.format == 'date-time') return TYPE_OVERRIDES['OffsetDateTime'] ?: 'OffsetDateTime'
      // format: uri maps to java.net.URI (openapi-generator default mapping).
      if (fragment.format == 'uri') return 'URI'
      // Note: inline enum strings and uri-reference are both handled as String.
      return 'String'
    case 'integer':
      return fragment.format == 'int64' ? 'Long' : 'Integer'
    case 'number':
      return fragment.format == 'float' ? 'Float' : 'Double'
    case 'boolean':
      return 'Boolean'
    case 'array':
      def collection = (fragment.uniqueItems == true) ? 'Set' : 'List'
      return "${collection}<${javaType(fragment.items as Map)}>".toString()
    case 'object':
      if (fragment.additionalProperties instanceof Map) {
        return "Map<String, ${javaType(fragment.additionalProperties as Map)}>".toString()
      }
      // additionalProperties: true (boolean) means an open map.
      if (fragment.additionalProperties instanceof Boolean && fragment.additionalProperties) {
        return 'Map<String, Object>'
      }
      return 'Object'
    default:
      return 'Object'
  }
}

// Variant of javaType that uses SIMPLE_TYPE_OVERRIDES instead of TYPE_OVERRIDES.
// Additionally, for named schemas with oneOf and no primitive type, returns the schema name
// rather than resolving into the oneOf variants — this prevents resolving union/discriminator
// types (like SourceElementInstruction) to a specific variant (like SourceElementIdInstruction).
// Used for computing x-simple-staged-chain and x-simple-optional-own-vars.
def simpleJavaType
simpleJavaType = { Map fragment ->
  if (fragment == null) return 'Object'
  if (fragment['$ref']) {
    def refName = ((String) fragment['$ref']).tokenize('/').last()
    if (SIMPLE_TYPE_OVERRIDES.containsKey(refName)) return SIMPLE_TYPE_OVERRIDES[refName]
    def refSchema = schemas[refName]
    if (refSchema instanceof Map) {
      def rs = refSchema as Map
      if (rs.enum != null) return refName
      if (rs.type == 'object') return refName
      if (rs.allOf != null && rs.type == null) return refName
      // Named schemas with oneOf and no type generate a named Java class — keep the name.
      if (rs.oneOf != null && rs.type == null) return refName
      def resolved = simpleJavaType(rs)
      if (resolved != 'Object') return resolved
    }
    return refName
  }
  if (fragment.allOf) {
    def ref = fragment.allOf.find { it['$ref'] }
    if (ref) {
      def refName = ((String) ref['$ref']).tokenize('/').last()
      if (SIMPLE_TYPE_OVERRIDES.containsKey(refName)) return SIMPLE_TYPE_OVERRIDES[refName]
      def refSchema = schemas[refName]
      if (refSchema instanceof Map) {
        def rs = refSchema as Map
        if (rs.enum != null) return refName
        if (rs.type == 'object') return refName
        if (rs.allOf != null && rs.type == null) return refName
        if (rs.oneOf != null && rs.type == null) return refName
        def resolved = simpleJavaType(rs)
        if (resolved != 'Object') return resolved
      }
      return refName
    }
  }
  if (fragment.oneOf) {
    def ref = fragment.oneOf.find { it['$ref'] }
    if (ref) {
      def refName = ((String) ref['$ref']).tokenize('/').last()
      if (SIMPLE_TYPE_OVERRIDES.containsKey(refName)) return SIMPLE_TYPE_OVERRIDES[refName]
      def refSchema = schemas[refName]
      if (refSchema instanceof Map) {
        def rs = refSchema as Map
        if (rs.enum != null) return refName
        if (rs.type == 'object') return refName
        if (rs.allOf != null && rs.type == null) return refName
        if (rs.oneOf != null && rs.type == null) return refName
        def resolved = simpleJavaType(rs)
        if (resolved != 'Object') return resolved
      }
      return refName
    }
  }
  switch (fragment.type) {
    case 'string':
      if (fragment.format == 'date-time') return SIMPLE_TYPE_OVERRIDES['OffsetDateTime'] ?: 'OffsetDateTime'
      if (fragment.format == 'uri') return 'URI'
      return 'String'
    case 'integer':
      return fragment.format == 'int64' ? 'Long' : 'Integer'
    case 'number':
      return fragment.format == 'float' ? 'Float' : 'Double'
    case 'boolean':
      return 'Boolean'
    case 'array':
      def collection = (fragment.uniqueItems == true) ? 'Set' : 'List'
      return "${collection}<${simpleJavaType(fragment.items as Map)}>".toString()
    case 'object':
      if (fragment.additionalProperties instanceof Map) {
        return "Map<String, ${simpleJavaType(fragment.additionalProperties as Map)}>".toString()
      }
      if (fragment.additionalProperties instanceof Boolean && fragment.additionalProperties) {
        return 'Map<String, Object>'
      }
      return 'Object'
    default:
      return 'Object'
  }
}

def pascalCase = { String s -> s ? s[0].toUpperCase() + s.substring(1) : s }

// Convert an OpenAPI property name (which may contain dots, hyphens, or underscores) to a valid
// Java camelCase identifier, mirroring openapi-generator's camelize behaviour.
// E.g. "camunda.document.type" -> "camundaDocumentType", "foo-bar" -> "fooBar".
def javaName = { String s ->
  if (!s) return s
  // Split on non-alphanumeric word separators: dot, hyphen, underscore
  def parts = s.split(/[.\-_]/)
  if (parts.size() == 1) return s  // no separators — already valid or handled elsewhere
  def result = new StringBuilder(parts[0])
  for (int i = 1; i < parts.length; i++) {
    def p = parts[i]
    if (p) result.append(p[0].toUpperCase() + (p.length() > 1 ? p.substring(1) : ''))
  }
  return result.toString()
}

// Pascal-case a Java identifier name (already camelize-corrected).
def pascalName = { String s -> s ? s[0].toUpperCase() + s.substring(1) : s }

// Resolve the Java type for a named property, handling inline enums.
// When a string property has inline enum values, openapi-generator creates an inner enum class
// named PascalCase(javaPropertyName) + "Enum". This function detects that case.
// For array-of-enum, the inner type becomes PascalCase(javaPropertyName) + "Enum" too
// (openapi-generator uses the container property's name for the items enum class).
def javaTypeForProperty = { Map propSchema, String javaFieldName ->
  if (propSchema == null) return 'Object'
  // Check for inline enum on a string property.
  if (propSchema.type == 'string' && propSchema.enum != null) {
    return "${pascalName(javaFieldName)}Enum".toString()
  }
  // Check for array of inline-enum items.
  if (propSchema.type == 'array' && propSchema.items instanceof Map) {
    def items = propSchema.items as Map
    if (items.type == 'string' && items.enum != null) {
      return "List<${pascalName(javaFieldName)}Enum>".toString()
    }
    if (items['$ref']) {
      def refName = ((String) items['$ref']).tokenize('/').last()
      // If the $ref schema itself is an enum, it will have its own generated enum class.
      def refSchema = schemas[refName]
      if (refSchema instanceof Map && (refSchema as Map).enum != null) {
        return "List<${refName}>".toString()  // top-level enum schema keeps its name
      }
    }
  }
  return javaType(propSchema)
}

// Variant of javaTypeForProperty that uses simpleJavaType for type resolution.
// Used when computing x-simple-staged-chain and x-simple-optional-own-vars.
def simpleJavaTypeForProperty = { Map propSchema, String javaFieldName ->
  if (propSchema == null) return 'Object'
  if (propSchema.type == 'string' && propSchema.enum != null) {
    return "${pascalName(javaFieldName)}Enum".toString()
  }
  if (propSchema.type == 'array' && propSchema.items instanceof Map) {
    def items = propSchema.items as Map
    if (items.type == 'string' && items.enum != null) {
      return "List<${pascalName(javaFieldName)}Enum>".toString()
    }
    if (items['$ref']) {
      def refName = ((String) items['$ref']).tokenize('/').last()
      def refSchema = schemas[refName]
      if (refSchema instanceof Map && (refSchema as Map).enum != null) {
        return "List<${refName}>".toString()
      }
    }
  }
  return simpleJavaType(propSchema)
}

// Find the property's schema fragment by name. Walks parent chain to find inherited properties.
// Also checks inside allOf inline objects (non-$ref entries) for properties declared via
// the allOf-with-inline-object pattern.
def findProperty
findProperty = { String schemaName, String propName ->
  def s = schemas[schemaName] as Map
  if (!s) return null
  // Check top-level properties first.
  if (s.properties instanceof Map && (s.properties as Map).containsKey(propName)) {
    return (s.properties as Map)[propName]
  }
  // Check inside allOf inline objects (non-$ref entries).
  if (s.allOf instanceof List) {
    for (def entry : (s.allOf as List)) {
      if (entry instanceof Map && !(entry as Map).containsKey('$ref')) {
        def ep = (entry as Map).get('properties')
        if (ep instanceof Map && (ep as Map).containsKey(propName)) {
          return (ep as Map)[propName]
        }
      }
    }
  }
  // Walk up the parent chain.
  def parent = parentOf[schemaName]
  return parent ? findProperty(parent, propName) : null
}

// --- all-ctor-args computation ---

// Compute the ordered list of constructor arg names for a schema.
// Order: own properties (in schema declaration order) first, then recursively the parent's
// ctor args. This mirrors what the generator puts in x-java-all-args-constructor-vars for
// classes where it sets x-java-all-args-constructor=true, and extends that to root classes.
// Used to emit x-all-ctor-args vendor extension for the pojo ctor and javaBuilder build().
//
// The parent's ctor arg order is the parent's own props + grandparent's own props + ...
// i.e., it's recursively defined via allCtorArgNames(parent).
def allCtorArgNames
allCtorArgNames = { String schemaName ->
  def s = schemas[schemaName]
  if (!(s instanceof Map)) return []
  def sm = s as Map

  // Collect own properties from the schema's top-level properties OR inline allOf objects.
  def ownPropNames = []
  def topProps = sm.get('properties')
  if (topProps instanceof Map) ownPropNames.addAll((topProps as Map).keySet())
  if (sm.allOf instanceof List) {
    (sm.allOf as List).each { entry ->
      if (entry instanceof Map && !(entry as Map).containsKey('$ref')) {
        def ep = (entry as Map).get('properties')
        if (ep instanceof Map) ownPropNames.addAll((ep as Map).keySet())
      }
    }
  }

  // Recursively add parent ctor args.
  def parent = parentOf[schemaName]
  def parentArgNames = parent ? allCtorArgNames(parent) : []

  return ownPropNames + parentArgNames
}

// --- x-staged-chain and x-optional-parent-vars injection ---

// Emit x-staged-chain on each schema with required fields.
// Also emit x-optional-parent-vars: parent vars that are NOT in the child's required list.
// This lets javaBuilder.mustache know which parent vars need optional setters in IBuild/Impl
// without risking duplicates with the staged chain.
schemas.each { name, schema ->
  if (!(schema instanceof Map)) return
  def merged = mergedRequired(name)
  def mergedSet = merged as Set

  // --- x-staged-chain ---
  if (!merged.isEmpty()) {
    def chain = []
    merged.eachWithIndex { propName, idx ->
      def propSchema = findProperty(name, propName)
      if (propSchema == null) {
        throw new IllegalStateException("Required property '${propName}' on schema '${name}' has no schema — spec error?")
      }
      def isLast = (idx == merged.size() - 1)
      def nextProp = isLast ? null : merged[idx + 1]
      def nullable = (propSchema instanceof Map) && propSchema.nullable == true
      def javaFieldName = javaName(propName)
      def nextJavaFieldName = nextProp ? javaName(nextProp) : null
      chain << [
        name             : javaFieldName,
        pascalName       : pascalName(javaFieldName),
        type             : javaTypeForProperty(propSchema as Map, javaFieldName),
        nullableAnnotation: nullable ? '@org.jspecify.annotations.Nullable ' : '',
        isLast           : isLast,
        nextStage        : nextJavaFieldName ? pascalName(nextJavaFieldName) : null,
      ]
    }
    schema['x-staged-chain'] = chain
    // Boolean flag so templates can use a non-iterating guard for x-staged-chain.
    schema['x-has-staged-chain'] = true

    // --- x-simple-staged-chain ---
    // Same structure as x-staged-chain but uses SIMPLE_TYPE_OVERRIDES for type resolution.
    // The simple generator maps many filter-property types (e.g. StringFilterProperty → String,
    // DateTimeFilterProperty → SimpleDateTimeFilterProperty) differently from the advanced
    // generator. Using simpleJavaTypeForProperty here keeps staged-builder setter types in
    // sync with the simple-variant pojo's actual field/ctor types.
    // Used exclusively by simple/javaBuilder.mustache.
    def simpleChain = []
    merged.eachWithIndex { propName, idx ->
      def propSchema = findProperty(name, propName)
      def isLast = (idx == merged.size() - 1)
      def nextProp = isLast ? null : merged[idx + 1]
      def nullable = (propSchema instanceof Map) && propSchema.nullable == true
      def javaFieldName = javaName(propName)
      def nextJavaFieldName = nextProp ? javaName(nextProp) : null
      simpleChain << [
        name             : javaFieldName,
        pascalName       : pascalName(javaFieldName),
        type             : simpleJavaTypeForProperty(propSchema as Map, javaFieldName),
        nullableAnnotation: nullable ? '@org.jspecify.annotations.Nullable ' : '',
        isLast           : isLast,
        nextStage        : nextJavaFieldName ? pascalName(nextJavaFieldName) : null,
      ]
    }
    schema['x-simple-staged-chain'] = simpleChain
  }

  // --- x-optional-parent-vars ---
  // Collect optional (non-required) vars from all ancestor schemas, in declaration order.
  // Used by javaBuilder.mustache to add parent optional setters to IBuild/Impl.
  def parents = parentChain(name)
  if (!parents.isEmpty()) {
    def optParentVars = []
    def seenNames = new LinkedHashSet<String>()
    parents.each { parentName ->
      def parentSchema = schemas[parentName] as Map
      def parentProps = parentSchema?.properties
      if (parentProps instanceof Map) {
        (parentProps as Map).each { propName, propSchema ->
          // Include only if NOT in the merged required list (i.e., truly optional for child).
          if (!mergedSet.contains(propName) && seenNames.add(propName)) {
            def nullable = (propSchema instanceof Map) && propSchema.nullable == true
            def javaFieldName = javaName((String) propName)
            optParentVars << [
              name             : javaFieldName,
              type             : javaTypeForProperty(propSchema as Map, javaFieldName),
              nullableAnnotation: nullable ? '@org.jspecify.annotations.Nullable ' : '',
            ]
          }
        }
      }
    }
    if (!optParentVars.isEmpty()) {
      schema['x-optional-parent-vars'] = optParentVars
    }
  }

  // --- x-needs-fallback-ctor ---
  // Set to true on schemas that have no own properties but extend a parent that HAS own
  // properties (and therefore will have an explicit all-args constructor). These child schemas
  // cannot rely on Java's implicit no-args constructor because the parent's explicit ctor
  // requires arguments. The pojo.mustache template uses this flag to emit a pass-through
  // constructor that delegates to super().
  //
  // Criterion: no own properties + has a parent + parent (or grandparent, recursively) has
  // own properties. The last condition ensures we don't trigger for pure mixin/marker
  // schemas that extend other empty schemas.
  //
  // "Own properties" means properties declared at the top level OR within an inline allOf
  // object entry (i.e., not from a $ref). The openapi normalizer (REF_AS_PARENT_IN_ALLOF=true)
  // treats $ref entries as parent, and inline objects as own.
  def schemaMap = schema as Map
  def topLevelProps = schemaMap.get('properties')
  def hasTopLevelProps = (topLevelProps instanceof Map) && !(topLevelProps as Map).isEmpty()
  def hasInlineAllOfProps = false
  if (!hasTopLevelProps && schemaMap.allOf instanceof List) {
    // Check if any allOf entry is an inline object (not a $ref) with properties.
    hasInlineAllOfProps = (schemaMap.allOf as List).any { entry ->
      if (!(entry instanceof Map)) return false
      def ep = (entry as Map).get('properties')
      return (ep instanceof Map) && !(ep as Map).isEmpty()
    }
  }
  def hasOwnProperties = hasTopLevelProps || hasInlineAllOfProps

  // --- x-needs-fallback-ctor ---
  // Set for schemas with NO own properties that have a parent in `parentOf` AND some ancestor
  // has own properties. Covers both true-extends and flat-inlined cases conservatively: the
  // template guard {{#parent}} in pojo.mustache ensures the ctor block is only emitted when the
  // generator actually produces a Java extends clause (parent is set in template context).
  // The javaBuilder template uses {{#parent}} similarly to avoid passing parentVars for flat classes.
  if (!hasOwnProperties && !parents.isEmpty()) {
    def ancestorHasExplicitCtor = parents.any { pName ->
      def ps = schemas[pName]
      if (!(ps instanceof Map)) return false
      def pp = (ps as Map).get('properties')
      def psHasOwnProps = (pp instanceof Map) && !(pp as Map).isEmpty()
      // Also check allOf inline properties in the ancestor.
      if (!psHasOwnProps && (ps as Map).allOf instanceof List) {
        psHasOwnProps = ((ps as Map).allOf as List).any { entry ->
          if (!(entry instanceof Map)) return false
          def ep = (entry as Map).get('properties')
          return (ep instanceof Map) && !(ep as Map).isEmpty()
        }
      }
      return psHasOwnProps
    }
    if (ancestorHasExplicitCtor) {
      schema['x-needs-fallback-ctor'] = true
    }
  }

  // --- x-needs-child-ctor ---
  // Set for schemas that HAVE own properties AND have a parent in `parentOf` AND some ancestor
  // is a root class (not in parentOf itself) with own properties. Covers both true-extends and
  // flat-inlined cases conservatively: the template guard {{#parent}} in pojo.mustache ensures
  // the ctor block is only emitted when the generator produces a Java extends clause.
  if (hasOwnProperties && !parents.isEmpty()) {
    def ancestorIsRootWithProps = parents.any { pName ->
      def ps = schemas[pName]
      if (!(ps instanceof Map)) return false
      // Root class (in EnrichSpec terms) = not in parentOf (no allOf $ref of its own).
      if (parentOf.containsKey(pName)) return false
      // Root class must have own properties to have an explicit ctor.
      def pp = (ps as Map).get('properties')
      def psHasOwnProps = (pp instanceof Map) && !(pp as Map).isEmpty()
      if (!psHasOwnProps && (ps as Map).allOf instanceof List) {
        psHasOwnProps = ((ps as Map).allOf as List).any { entry ->
          if (!(entry instanceof Map)) return false
          def ep = (entry as Map).get('properties')
          return (ep instanceof Map) && !(ep as Map).isEmpty()
        }
      }
      return psHasOwnProps
    }
    if (ancestorIsRootWithProps) {
      schema['x-needs-child-ctor'] = true
    }
  }

  // --- x-all-ctor-args ---
  // Emit for ALL schemas to provide the full ctor arg list in declaration order for use in
  // javaBuilder's build() method. This lets the template reference Impl's local fields safely
  // from a static context in the right order to match the pojo ctor's {{#vars}} parameter list.
  //
  // The javaBuilder template uses x-all-ctor-args as a fallback when:
  // - x-java-all-args-constructor-vars is empty, AND
  // - the generator's {{parent}} is not set (flat class / root class) — guarded by {{^parent}}
  //
  // For true-extends classes ({{parent}} is set), x-needs-fallback-ctor or x-needs-child-ctor
  // handle the arg passing, and x-all-ctor-args is shadowed by the {{^parent}} guard.
  //
  // allCtorArgNames() collects property names in declaration order (top-level + inline allOf),
  // which matches the pojo root-class ctor's {{#vars}} order.
  def rawArgNames = allCtorArgNames((String) name)
  if (!rawArgNames.isEmpty()) {
    schema['x-all-ctor-args'] = rawArgNames.collect { propName ->
      [name: javaName((String) propName)]
    }
  }

  // --- x-optional-own-vars ---
  // Collect optional (non-required) vars from the schema's OWN properties, in declaration order.
  // "Own" includes: top-level properties, inline allOf object entries (not $ref), and properties
  // from oneOf/anyOf $ref targets that the generator merges into a flat class.
  //
  // Used by the simple-variant javaBuilder.mustache as a type-correct replacement for the
  // vars-inverted-required iteration. In the simple variant, flat-inlined schemas may have
  // inherited-and-required fields appear as own vars with required=false in the generator's
  // context (because the property was declared optional in an ancestor). This extension avoids
  // emitting duplicate optional setters for those fields by applying the same mergedRequired
  // logic as x-staged-chain. nullableAnnotation is pre-stored (same convention as
  // x-optional-parent-vars) so the template doesn't need to call the nullableAnnotation partial.
  def ownOptVars = []
  def seenOwnNames = new LinkedHashSet<String>()
  // Helper closure to add optional vars from a properties map.
  def addOptVarsFromProps = { Map propsMap ->
    propsMap.each { propName, propSchema ->
      if (!mergedSet.contains(propName) && seenOwnNames.add(propName)) {
        def nullable = (propSchema instanceof Map) && (propSchema as Map).nullable == true
        def javaFieldName = javaName((String) propName)
        ownOptVars << [
          name             : javaFieldName,
          type             : javaTypeForProperty(propSchema as Map, javaFieldName),
          nullableAnnotation: nullable ? '@org.jspecify.annotations.Nullable ' : '',
        ]
      }
    }
  }
  // 1. Top-level properties.
  def ownTopLevelProps = schemaMap.get('properties')
  if (ownTopLevelProps instanceof Map) addOptVarsFromProps(ownTopLevelProps as Map)
  // 2. Inline allOf objects (non-$ref entries) — own properties expressed via allOf pattern.
  if (schemaMap.allOf instanceof List) {
    (schemaMap.allOf as List).each { entry ->
      if (entry instanceof Map && !(entry as Map).containsKey('$ref')) {
        def ep = (entry as Map).get('properties')
        if (ep instanceof Map) addOptVarsFromProps(ep as Map)
      }
    }
  }
  // 3. oneOf / anyOf $ref targets — the simple generator merges these into a flat class;
  //    we scan each $ref target's properties so x-optional-own-vars covers all merged vars.
  def mergedOneOfTargets = []
  if (schemaMap.oneOf instanceof List) mergedOneOfTargets.addAll((schemaMap.oneOf as List))
  if (schemaMap.anyOf instanceof List) mergedOneOfTargets.addAll((schemaMap.anyOf as List))
  mergedOneOfTargets.each { entry ->
    if (!(entry instanceof Map)) return
    def em = entry as Map
    def refProps = null
    if (em['$ref']) {
      def refName = ((String) em['$ref']).tokenize('/').last()
      def refSchema = schemas[refName]
      if (refSchema instanceof Map) refProps = (refSchema as Map).get('properties')
    }
    if (refProps instanceof Map) addOptVarsFromProps(refProps as Map)
  }
  if (!ownOptVars.isEmpty()) {
    schema['x-optional-own-vars'] = ownOptVars
  }

  // --- x-simple-optional-own-vars ---
  // Same as x-optional-own-vars but uses simpleJavaTypeForProperty for type resolution.
  // The simple generator maps filter-property types to String/Integer/etc., so types computed
  // with javaTypeForProperty (which uses TYPE_OVERRIDES for advanced types) would not match the
  // simple-variant ctor/field types. Used exclusively by simple/javaBuilder.mustache.
  //
  // Also includes optional properties from the allOf $ref parent chain (not just own props).
  // For flat-inlined classes in the simple variant (no Java parent despite having an allOf $ref
  // parent in YAML), the parent's optional props must be included so that Impl fields and
  // IBuild optional setters cover all settable vars.
  def simpleOwnOptVars = []
  def seenSimpleOwnNames = new LinkedHashSet<String>()
  def addSimpleOptVarsFromProps = { Map propsMap ->
    propsMap.each { propName, propSchema ->
      if (!mergedSet.contains(propName) && seenSimpleOwnNames.add(propName)) {
        def nullable = (propSchema instanceof Map) && (propSchema as Map).nullable == true
        def javaFieldName = javaName((String) propName)
        simpleOwnOptVars << [
          name             : javaFieldName,
          type             : simpleJavaTypeForProperty(propSchema as Map, javaFieldName),
          nullableAnnotation: nullable ? '@org.jspecify.annotations.Nullable ' : '',
        ]
      }
    }
  }
  // 1. Top-level properties.
  if (ownTopLevelProps instanceof Map) addSimpleOptVarsFromProps(ownTopLevelProps as Map)
  // 2. Inline allOf objects (non-$ref entries).
  if (schemaMap.allOf instanceof List) {
    (schemaMap.allOf as List).each { entry ->
      if (entry instanceof Map && !(entry as Map).containsKey('$ref')) {
        def ep = (entry as Map).get('properties')
        if (ep instanceof Map) addSimpleOptVarsFromProps(ep as Map)
      }
    }
  }
  // 3. Full parent chain properties — needed for flat-inlined classes in the simple variant.
  //    When the simple generator flat-inlines a parent (no Java extends), the parent's optional
  //    props must be in x-simple-optional-own-vars since x-optional-parent-vars is guarded by
  //    {{#parent}} in the simple template and won't fire for flat-inlined classes.
  //    Walk the entire parent chain (not just one level) to cover grandparent properties.
  def allParents = parentChain((String) name)
  allParents.each { parentName ->
    def parentSchemaMap = schemas[parentName] as Map
    if (parentSchemaMap == null) return
    def parentProps = parentSchemaMap.get('properties')
    if (parentProps instanceof Map) addSimpleOptVarsFromProps(parentProps as Map)
  }
  // 4. oneOf / anyOf $ref targets (same as x-optional-own-vars).
  mergedOneOfTargets.each { entry ->
    if (!(entry instanceof Map)) return
    def em = entry as Map
    def refProps = null
    if (em['$ref']) {
      def refName = ((String) em['$ref']).tokenize('/').last()
      def refSchema = schemas[refName]
      if (refSchema instanceof Map) refProps = (refSchema as Map).get('properties')
    }
    if (refProps instanceof Map) addSimpleOptVarsFromProps(refProps as Map)
  }
  if (!simpleOwnOptVars.isEmpty()) {
    schema['x-simple-optional-own-vars'] = simpleOwnOptVars
  }
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
