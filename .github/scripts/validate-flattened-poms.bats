#!/usr/bin/env bats

# Tests for validate-flattened-poms.sh — verifies required Maven Central metadata
# checks and the Javadoc-deployability check (maven.javadoc.skip=true must be paired
# with an empty-javadoc-jar placeholder) across the local parent chain.
#
# Fixtures are built at runtime under BATS_TEST_TMPDIR: each test writes a minimal
# module tree (source pom.xml + generated .flattened-pom.xml, optionally a parent
# chain) and runs the script against that isolated root.

SCRIPT="${BATS_TEST_DIRNAME}/validate-flattened-poms.sh"

setup() {
  ROOT="${BATS_TEST_TMPDIR}/repo"
  mkdir -p "${ROOT}"
}

# Writes a complete, Maven-Central-valid .flattened-pom.xml (all required metadata
# present) at the given path, so the metadata check passes and tests isolate the
# Javadoc logic. Extra child XML can be appended via stdin.
write_flattened() {
  local dir="$1" packaging="${2:-jar}"
  mkdir -p "${dir}"
  cat > "${dir}/.flattened-pom.xml" <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.camunda</groupId>
  <artifactId>$(basename "${dir}")</artifactId>
  <version>1.0.0</version>
  <packaging>${packaging}</packaging>
  <name>Test Module</name>
  <description>A test module</description>
  <url>https://example.com</url>
  <licenses><license><name>Apache-2.0</name></license></licenses>
  <developers><developer><name>Camunda</name></developer></developers>
  <scm><url>https://example.com/scm</url></scm>
</project>
EOF
}

# ── Metadata checks (pre-existing behaviour: regression guard) ────────────────

@test "should pass for a jar module with complete metadata and no javadoc skip" {
  # given a jar module with full metadata and no maven.javadoc.skip
  write_flattened "${ROOT}/mod"
  cat > "${ROOT}/mod/pom.xml" <<'EOF'
<project><modelVersion>4.0.0</modelVersion>
  <groupId>io.camunda</groupId><artifactId>mod</artifactId><version>1.0.0</version>
</project>
EOF
  # when validating
  run bash "${SCRIPT}" "${ROOT}"
  # then it passes
  [ "$status" -eq 0 ]
}

@test "should fail when required metadata is missing from the flattened pom" {
  # given a flattened pom missing name/description/etc.
  mkdir -p "${ROOT}/mod"
  cat > "${ROOT}/mod/.flattened-pom.xml" <<'EOF'
<project><modelVersion>4.0.0</modelVersion>
  <groupId>io.camunda</groupId><artifactId>mod</artifactId><version>1.0.0</version>
</project>
EOF
  cat > "${ROOT}/mod/pom.xml" <<'EOF'
<project><modelVersion>4.0.0</modelVersion>
  <groupId>io.camunda</groupId><artifactId>mod</artifactId><version>1.0.0</version>
</project>
EOF
  # when validating
  run bash "${SCRIPT}" "${ROOT}"
  # then it fails on missing metadata
  [ "$status" -eq 1 ]
  [[ "$output" == *"missing required metadata"* ]]
}

@test "should fail when input has no flattened poms" {
  # given a root with no .flattened-pom.xml files
  # when validating
  run bash "${SCRIPT}" "${ROOT}"
  # then it fails clearly
  [ "$status" -eq 1 ]
  [[ "$output" == *"no .flattened-pom.xml files were found"* ]]
}

# ── Javadoc deployability check ───────────────────────────────────────────────

@test "should fail when jar module skips javadoc but has no empty-javadoc-jar" {
  # given a jar module with maven.javadoc.skip=true and no placeholder execution
  write_flattened "${ROOT}/mod"
  cat > "${ROOT}/mod/pom.xml" <<'EOF'
<project><modelVersion>4.0.0</modelVersion>
  <groupId>io.camunda</groupId><artifactId>mod</artifactId><version>1.0.0</version>
  <properties><maven.javadoc.skip>true</maven.javadoc.skip></properties>
</project>
EOF
  # when validating
  run bash "${SCRIPT}" "${ROOT}"
  # then it fails on the missing javadoc placeholder
  [ "$status" -eq 1 ]
  [[ "$output" == *"Javadocs must be provided"* ]]
}

@test "should pass when jar module skips javadoc but declares empty-javadoc-jar in build/plugins" {
  # given a jar module that skips javadoc and configures the placeholder in build/plugins
  write_flattened "${ROOT}/mod"
  cat > "${ROOT}/mod/pom.xml" <<'EOF'
<project><modelVersion>4.0.0</modelVersion>
  <groupId>io.camunda</groupId><artifactId>mod</artifactId><version>1.0.0</version>
  <properties><maven.javadoc.skip>true</maven.javadoc.skip></properties>
  <build><plugins>
    <plugin>
      <artifactId>maven-jar-plugin</artifactId>
      <executions><execution><id>empty-javadoc-jar</id><goals><goal>jar</goal></goals></execution></executions>
    </plugin>
  </plugins></build>
</project>
EOF
  # when validating
  run bash "${SCRIPT}" "${ROOT}"
  # then it passes
  [ "$status" -eq 0 ]
}

@test "should pass when jar module does not skip javadoc at all" {
  # given a jar module with no maven.javadoc.skip anywhere
  write_flattened "${ROOT}/mod"
  cat > "${ROOT}/mod/pom.xml" <<'EOF'
<project><modelVersion>4.0.0</modelVersion>
  <groupId>io.camunda</groupId><artifactId>mod</artifactId><version>1.0.0</version>
</project>
EOF
  # when validating
  run bash "${SCRIPT}" "${ROOT}"
  # then the javadoc check does not apply and it passes
  [ "$status" -eq 0 ]
}

@test "should skip the javadoc check for non-jar (pom) packaged modules" {
  # given a pom-packaged module that skips javadoc and has no placeholder
  write_flattened "${ROOT}/mod" "pom"
  cat > "${ROOT}/mod/pom.xml" <<'EOF'
<project><modelVersion>4.0.0</modelVersion>
  <groupId>io.camunda</groupId><artifactId>mod</artifactId><version>1.0.0</version>
  <packaging>pom</packaging>
  <properties><maven.javadoc.skip>true</maven.javadoc.skip></properties>
</project>
EOF
  # when validating
  run bash "${SCRIPT}" "${ROOT}"
  # then the javadoc check is not applied and it passes
  [ "$status" -eq 0 ]
}

# ── Parent-chain resolution ───────────────────────────────────────────────────

@test "should resolve skip and placeholder inherited from the parent chain" {
  # given a parent declaring skip=true + empty-javadoc-jar in pluginManagement,
  # and a child jar module that inherits both via default relativePath (../pom.xml)
  cat > "${ROOT}/pom.xml" <<'EOF'
<project><modelVersion>4.0.0</modelVersion>
  <groupId>io.camunda</groupId><artifactId>parent</artifactId><version>1.0.0</version>
  <packaging>pom</packaging>
  <properties><maven.javadoc.skip>true</maven.javadoc.skip></properties>
  <build><pluginManagement><plugins>
    <plugin>
      <artifactId>maven-jar-plugin</artifactId>
      <executions><execution><id>empty-javadoc-jar</id><goals><goal>jar</goal></goals></execution></executions>
    </plugin>
  </plugins></pluginManagement></build>
</project>
EOF
  write_flattened "${ROOT}/child"
  cat > "${ROOT}/child/pom.xml" <<'EOF'
<project><modelVersion>4.0.0</modelVersion>
  <parent><groupId>io.camunda</groupId><artifactId>parent</artifactId><version>1.0.0</version></parent>
  <artifactId>child</artifactId>
</project>
EOF
  # when validating
  run bash "${SCRIPT}" "${ROOT}"
  # then the inherited placeholder satisfies the check and it passes
  [ "$status" -eq 0 ]
}

@test "should stop at an explicit empty relativePath and not resolve the parent locally" {
  # given a parent that skips javadoc with no placeholder, and a child jar module
  # whose empty <relativePath/> disables local parent resolution
  cat > "${ROOT}/pom.xml" <<'EOF'
<project><modelVersion>4.0.0</modelVersion>
  <groupId>io.camunda</groupId><artifactId>parent</artifactId><version>1.0.0</version>
  <packaging>pom</packaging>
  <properties><maven.javadoc.skip>true</maven.javadoc.skip></properties>
</project>
EOF
  write_flattened "${ROOT}/child"
  cat > "${ROOT}/child/pom.xml" <<'EOF'
<project><modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.camunda</groupId><artifactId>parent</artifactId><version>1.0.0</version>
    <relativePath/>
  </parent>
  <artifactId>child</artifactId>
</project>
EOF
  # when validating
  run bash "${SCRIPT}" "${ROOT}"
  # then the parent's skip is not seen locally, so the javadoc check does not fire
  [ "$status" -eq 0 ]
}

@test "should terminate on a cyclic parent chain rather than loop forever" {
  # given two jar modules whose relativePath entries point at each other, one setting
  # skip=true, neither declaring a placeholder
  mkdir -p "${ROOT}/a" "${ROOT}/b"
  write_flattened "${ROOT}/a"
  cat > "${ROOT}/a/pom.xml" <<'EOF'
<project><modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.camunda</groupId><artifactId>b</artifactId><version>1.0.0</version>
    <relativePath>../b/pom.xml</relativePath>
  </parent>
  <artifactId>a</artifactId>
  <properties><maven.javadoc.skip>true</maven.javadoc.skip></properties>
</project>
EOF
  cat > "${ROOT}/b/pom.xml" <<'EOF'
<project><modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.camunda</groupId><artifactId>a</artifactId><version>1.0.0</version>
    <relativePath>../a/pom.xml</relativePath>
  </parent>
  <artifactId>b</artifactId>
</project>
EOF
  # when validating (guarded by a timeout to catch an infinite loop)
  run timeout 30 bash "${SCRIPT}" "${ROOT}"
  # then it terminates (not a 124 timeout) and reports the missing placeholder
  [ "$status" -eq 1 ]
  [[ "$output" == *"Javadocs must be provided"* ]]
}
