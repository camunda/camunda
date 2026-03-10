
declare -A lookupTeamMedic

# @core-features-medic
coreFeaturesMedic="<!subteam^S08P2CU9V8W|core-features-medic>"
lookupTeamMedic["team-core-features"]=$coreFeaturesMedic
lookupTeamMedic["Core Features"]=$coreFeaturesMedic
lookupTeamMedic["CoreFeatures"]=$coreFeaturesMedic
lookupTeamMedic["@camunda/core-features"]=$coreFeaturesMedic

# @data-layer-medic
dataLayerMedic="<!subteam^S08P2CSC06S|data-layer-medic>"
lookupTeamMedic["team-data-layer"]=$dataLayerMedic
lookupTeamMedic["Data Layer"]=$dataLayerMedic
lookupTeamMedic["DataLayer"]=$dataLayerMedic
lookupTeamMedic["@camunda/data-layer"]=$dataLayerMedic

# @identity-medic
identityMedic="<!subteam^S053MF48SSH|identity-medic>"
lookupTeamMedic["team-identity"]=$identityMedic
lookupTeamMedic["Identity"]=$identityMedic
lookupTeamMedic["@camunda/identity"]=$identityMedic
lookupTeamMedic["@camunda/identity-frontend"]=$identityMedic

# @distributed-systems-medic
distributedSystemsMedic="<!subteam^S09B7LXB77D|distributed-systems-medic>"
lookupTeamMedic["team-distributed-systems"]=$distributedSystemsMedic
lookupTeamMedic["Distributed Systems"]=$distributedSystemsMedic
lookupTeamMedic["DistributedSystems"]=$distributedSystemsMedic
lookupTeamMedic["@camunda/zeebe-distributed-platform"]=$distributedSystemsMedic

# @camunda-ex-medic
camundaExMedic="<!subteam^S064J3N99A5|camunda-ex-medic>"
lookupTeamMedic["Camunda Ex"]=$camundaExMedic
lookupTeamMedic["CamundaEx"]=$camundaExMedic
lookupTeamMedic["@camunda/camundaex"]=$camundaExMedic

# @distro-medic
distroMedic="<!subteam^S053K7C7QKU|distro-medic>"
lookupTeamMedic["@camunda/distribution"]=$distroMedic

# @reliability-testing-team
reliabilityTestingTeam="<!subteam^S0A1Q2TJ6MB|reliability-testing-team>"
lookupTeamMedic["@camunda/reliability-testing"]=$reliabilityTestingTeam

# @qa-medic
qaMedic="<!subteam^S09UBFWENKF|qa-medic>"
lookupTeamMedic["@camunda/qa-engineering"]=$qaMedic

# @monorepo-ci-medic
monorepoCIMedic="<!subteam^S07D6C6B18T|monorepo-ci-medic>"
lookupTeamMedic["@camunda/monorepo-devops-team"]=$monorepoCIMedic

# failure in QA test
lookupTeamMedic["QA"]="QA Acceptance Test, requires investigation"

# catch all for tests without an assigned team
lookupTeamMedic["General"]="General Test, requires investigation"


resolve_test_source_file() {
    # Resolves a Java test class FQCN to the exact repo-relative test source file path.
    #
    # Input: fully qualified test class name, e.g. io.camunda.foo.BarTest
    # Output: prints a single repo-relative path or empty string.
    local fqcn="$1"
    local rel_test_path="${fqcn//./\/}.java"

    # Find the test file location (first match is good enough)
    local test_file
    test_file="$(git ls-files "**/src/test/java/${rel_test_path}" 2>/dev/null | head -n 1)"
    if [[ -z "${test_file}" ]]; then
        # Some tests live under integrationTest or other conventions
        test_file="$(git ls-files "**/src/*Test/java/${rel_test_path}" 2>/dev/null | head -n 1)"
    fi

    if [[ -n "${test_file}" && -f "${test_file}" ]]; then
        echo "${test_file}"
        return 0
    fi

    echo ""
}

resolve_codeowners_team() {
    # Input: repo-relative file path
    # Output: prints first required owner or empty string
    local file_path="$1"

    if [[ -z "${file_path}" || ! -f "${file_path}" ]]; then
        echo ""
        return 0
    fi

    codeowners-cli owner --format json "${file_path}" 2>/dev/null | jq -r --arg f "${file_path}" '(.[$f].required // [])[0] // ""' || true
}
