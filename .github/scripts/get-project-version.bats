#!/usr/bin/env bats

# Tests for get-project-version.sh — verifies the version is read from the root
# POM's <parent> block, and that the guards fire instead of emitting an empty
# version (which downstream becomes a bad image tag).
#
# Fixtures are written at runtime under BATS_TEST_TMPDIR.

SCRIPT="${BATS_TEST_DIRNAME}/get-project-version.sh"

# Writes a root POM shaped like the repository's own: no <version> of its own,
# inheriting the given one from <parent>.
write_root_pom() {
  cat > "${BATS_TEST_TMPDIR}/pom.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">

  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>io.camunda</groupId>
    <artifactId>zeebe-parent</artifactId>
    <version>$1</version>
    <relativePath>parent/pom.xml</relativePath>
  </parent>

  <artifactId>camunda-8-root</artifactId>
  <packaging>pom</packaging>

  <build>
    <plugins>
      <plugin>
        <version>\${plugin.version.ktfmt}</version>
      </plugin>
    </plugins>
  </build>

</project>
EOF
}

@test "reads the inherited SNAPSHOT version" {
  write_root_pom "8.9.28-SNAPSHOT"
  run "${SCRIPT}" "${BATS_TEST_TMPDIR}/pom.xml"
  [ "${status}" -eq 0 ]
  [ "${output}" = "8.9.28-SNAPSHOT" ]
}

@test "reads the inherited release version" {
  write_root_pom "8.10.9"
  run "${SCRIPT}" "${BATS_TEST_TMPDIR}/pom.xml"
  [ "${status}" -eq 0 ]
  [ "${output}" = "8.10.9" ]
}

@test "ignores plugin versions further down the POM" {
  write_root_pom "8.11.0-SNAPSHOT"
  run "${SCRIPT}" "${BATS_TEST_TMPDIR}/pom.xml"
  [ "${status}" -eq 0 ]
  [ "${output}" = "8.11.0-SNAPSHOT" ]
}

@test "fails when the root POM declares its own version" {
  write_root_pom "8.11.0-SNAPSHOT"
  sed -i'' -e 's|^  <artifactId>camunda-8-root</artifactId>|  <version>9.9.9</version>\n  <artifactId>camunda-8-root</artifactId>|' \
    "${BATS_TEST_TMPDIR}/pom.xml"
  run "${SCRIPT}" "${BATS_TEST_TMPDIR}/pom.xml"
  [ "${status}" -eq 1 ]
  [[ "${output}" == *"declares its own <version>"* ]]
}

@test "fails when the parent version is a property reference" {
  write_root_pom '${revision}'
  run "${SCRIPT}" "${BATS_TEST_TMPDIR}/pom.xml"
  [ "${status}" -eq 1 ]
  [[ "${output}" == *"no usable version"* ]]
}

@test "fails when the POM does not exist" {
  run "${SCRIPT}" "${BATS_TEST_TMPDIR}/missing.xml"
  [ "${status}" -eq 1 ]
  [[ "${output}" == *"not found"* ]]
}

@test "defaults to pom.xml in the working directory" {
  write_root_pom "8.8.45-SNAPSHOT"
  cd "${BATS_TEST_TMPDIR}"
  run "${SCRIPT}"
  [ "${status}" -eq 0 ]
  [ "${output}" = "8.8.45-SNAPSHOT" ]
}
