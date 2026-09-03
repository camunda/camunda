"""Unit tests for the AlwaysGreen classifier.

Cases are drawn from real failed runs of docker-build-helm-integration.yml so each
one pins a mistake that was actually observed rather than a hypothetical.
"""

from __future__ import annotations

import classify


# ---------------------------------------------------------------------------
# Platform noise
# ---------------------------------------------------------------------------


def test_platform_internal_error_is_noise():
    # Run 30157503817: Cleanup and Observe both failed this way, no steps ran.
    verdict = classify.noise_verdict(
        conclusion="failure",
        step_count=0,
        failure_annotations=[
            "GitHub Actions has encountered an internal error when running your job."
        ],
    )
    assert verdict == classify.NOISE_PLATFORM


def test_cancellation_is_noise_even_with_steps():
    # Run 30078726133: Build and Push Docker Images had 20 steps but was cancelled,
    # which the first census mistook for a real build failure.
    verdict = classify.noise_verdict(
        conclusion="failure",
        step_count=20,
        failure_annotations=["The operation was canceled."],
    )
    assert verdict == classify.NOISE_CANCELLED


def test_no_steps_and_no_annotation_is_noise():
    # Downstream run 30157726486: Create cluster generation on INT.
    verdict = classify.noise_verdict(
        conclusion="failure", step_count=0, failure_annotations=[]
    )
    assert verdict == classify.NOISE_NO_EVIDENCE


def test_ordinary_step_failure_is_not_noise():
    verdict = classify.noise_verdict(
        conclusion="failure",
        step_count=30,
        failure_annotations=["Process completed with exit code 1."],
    )
    assert verdict is None


def test_non_failure_conclusions_are_not_classified():
    assert (
        classify.noise_verdict(
            conclusion="success", step_count=0, failure_annotations=[]
        )
        is None
    )


# ---------------------------------------------------------------------------
# Surface classification
# ---------------------------------------------------------------------------


def test_nested_sm_e2e_job_name_maps_to_sm_surface():
    name = (
        "Helm chart Integration Tests / agrn - install - gke / "
        "Playwright e2e after install - install on gke - agrn (1 of 1)"
    )
    assert classify.surface_for_job(name) == classify.SURFACE_SM_E2E


def test_unrendered_template_job_name_still_matches_prefix():
    # Skipped jobs keep the raw expressions; the prefix is the stable part.
    name = (
        "Helm chart Integration Tests / agrn - install - gke / "
        "Playwright e2e after install - ${{ inputs.flow }} on "
        "${{ inputs.distro-platform }} - ${{ inputs.shortname }}"
    )
    assert classify.surface_for_job(name) == classify.SURFACE_SM_E2E


def test_sm_e2e_job_name_with_matrix_suite_maps_to_sm_surface():
    # camunda-platform-helm#6841 inserted `${{ matrix.suite }}` between "e2e"
    # and "after install" to run a named suite (e.g. the alwaysgreen scenario's
    # "full" suite), rendering e.g. run 31684711833's failing job. A plain
    # `startswith("Playwright e2e after install")` stops matching this and the
    # job silently drops out of triage with no evidence and no fix agent run.
    name = (
        "Helm chart Integration Tests / agrn - install - gke / "
        "Playwright e2e full after install - install on gke - agrn (1 of 1)"
    )
    assert classify.surface_for_job(name) == classify.SURFACE_SM_E2E

    smoke_name = name.replace("full after install", "smoke after install")
    assert classify.surface_for_job(smoke_name) == classify.SURFACE_SM_E2E


def test_unrendered_matrix_suite_job_name_still_matches_prefix():
    # The unrendered form of `${{ matrix.suite }}` itself contains spaces, so
    # a `\S+`-based fix for the case above would regress this one.
    name = (
        "Helm chart Integration Tests / agrn - install - gke / "
        "Playwright e2e ${{ matrix.suite }} after install - "
        "${{ inputs.flow }} on ${{ inputs.distro-platform }} - "
        "${{ inputs.shortname }}"
    )
    assert classify.surface_for_job(name) == classify.SURFACE_SM_E2E


def test_observe_status_job_is_ignored():
    assert classify.surface_for_job("Observe Helm chart Integration Tests status") is None


def test_frontend_and_docker_builds_map_to_build():
    assert classify.surface_for_job("Build Operate Frontend") == classify.SURFACE_BUILD
    assert (
        classify.surface_for_job("Build and Push Docker Images")
        == classify.SURFACE_BUILD
    )


def test_helm_install_and_cleanup_are_distinct_surfaces():
    base = "Helm chart Integration Tests / agrn - install - gke / "
    assert (
        classify.surface_for_job(base + "install for install on gke - agrn")
        == classify.SURFACE_HELM_INSTALL
    )
    assert (
        classify.surface_for_job(base + "Cleanup - install on gke - agrn")
        == classify.SURFACE_HELM_CLEANUP
    )


def test_dispatchable_surfaces():
    assert classify.SURFACE_SM_E2E in classify.DISPATCHABLE_SURFACES
    assert classify.SURFACE_SAAS_E2E in classify.DISPATCHABLE_SURFACES
    # A downstream run that went red on a job rather than a spec is still a bug
    # in a workflow this org owns, so it goes to the agent like any other.
    assert classify.SURFACE_SAAS_CI in classify.DISPATCHABLE_SURFACES
    # Dispatchable, but gated further by helm_install_verdict.
    assert classify.SURFACE_HELM_INSTALL in classify.DISPATCHABLE_SURFACES
    assert classify.SURFACE_BUILD not in classify.DISPATCHABLE_SURFACES
    assert classify.SURFACE_HELM_CLEANUP not in classify.DISPATCHABLE_SURFACES
    assert classify.SURFACE_SAAS_INFRA not in classify.DISPATCHABLE_SURFACES


# ---------------------------------------------------------------------------
# Playwright report parsing
# ---------------------------------------------------------------------------


def _nested_report(specs):
    """Report shaped like Playwright's real output: file suite → describe suite."""
    return {
        "config": {"rootDir": "/w/node_modules/@camunda/e2e-test-suite/dist/tests/SM-8.10"},
        "suites": [
            {
                "title": "smoke-tests.spec.js",
                "file": "smoke-tests.spec.js",
                "specs": [],
                "suites": [{"title": "Smoke Tests", "specs": specs}],
            }
        ],
    }


def test_counting_walks_nested_suites():
    # The pipeline's own one-level walk returns 0 here; that is the bug this
    # guards. Real example: downstream run 30259132560 was 15 specs / 2 failures.
    report = _nested_report(
        [
            {"file": "test-setup.spec.ts", "title": "a", "ok": False, "tests": [{"results": [{"status": "failed"}]}]},
            {"file": "smoke-tests.spec.ts", "title": "b", "ok": True, "tests": [{"results": [{"status": "passed"}]}]},
        ]
    )
    counts = classify.count_specs(report)
    assert counts.total == 2
    assert counts.failed == 1
    assert counts.setup_failed == 1


def test_flaky_counts_retried_specs_that_passed():
    report = _nested_report(
        [
            {
                "file": "smoke-tests.spec.ts",
                "title": "eventually passed",
                "ok": True,
                "tests": [{"results": [{"status": "failed"}, {"status": "passed"}]}],
            }
        ]
    )
    counts = classify.count_specs(report)
    assert counts.flaky == 1
    assert counts.failed == 0


def test_empty_report_counts_zero():
    assert classify.count_specs({"suites": []}) == classify.SpecCounts()


# ---------------------------------------------------------------------------
# SaaS sub-classification
# ---------------------------------------------------------------------------


def test_setup_only_failures_still_reach_the_agent():
    # Downstream run 33483343722: every failing spec was in test-setup.spec.ts, and
    # the trace shows a healthy app with the button on screen, failing on a retry
    # where the project already existed. Classifying on the file name dropped this
    # with no dispatch; the file mixes provisioning with ordinary UI flows, and
    # nothing in the report separates the two.
    counts = classify.SpecCounts(total=15, failed=2, flaky=0, setup_failed=2)
    assert (
        classify.saas_surface_from_counts(counts, has_artifacts=True)
        == classify.SURFACE_SAAS_E2E
    )


def test_real_test_failures_are_saas_e2e():
    counts = classify.SpecCounts(total=15, failed=2, flaky=0, setup_failed=0)
    assert (
        classify.saas_surface_from_counts(counts, has_artifacts=True)
        == classify.SURFACE_SAAS_E2E
    )


def test_setup_failures_are_still_counted_for_the_log():
    # Not a gate any more, but the triage log still reports it, so a run whose
    # failures are all in setup specs stays visible as such without being dropped.
    report = _nested_report(
        [
            {"file": "test-setup.spec.ts", "title": "a", "ok": False, "tests": [{"results": [{"status": "failed"}]}]},
        ]
    )
    counts = classify.count_specs(report)
    assert counts.failed == 1
    assert counts.setup_failed == 1


def test_no_failing_specs_with_reports_is_a_ci_job_failure():
    # Downstream run 31357325723: 15 specs, all green, red because the
    # `Delete created organizations` job died on a transient curl error.
    counts = classify.SpecCounts(total=15, failed=0)
    assert (
        classify.saas_surface_from_counts(counts, has_artifacts=True)
        == classify.SURFACE_SAAS_CI
    )


def test_missing_artifacts_is_infra():
    assert (
        classify.saas_surface_from_counts(classify.SpecCounts(), has_artifacts=False)
        == classify.SURFACE_SAAS_INFRA
    )


def test_reports_present_but_empty_is_infra():
    # Reports uploaded with no specs in them means Playwright never ran; the
    # failure is upstream of anything a CI-job fix could address.
    counts = classify.SpecCounts(total=0, failed=0)
    assert (
        classify.saas_surface_from_counts(counts, has_artifacts=True)
        == classify.SURFACE_SAAS_INFRA
    )


# ---------------------------------------------------------------------------
# CI-job specs
# ---------------------------------------------------------------------------


def test_ci_job_spec_points_at_the_owning_workflow():
    spec = classify.ci_job_spec(
        "Delete created organizations",
        workflow_path=".github/workflows/playwright_saas_pr_trigger_monorepo.yml",
        failing_steps=["Delete orgs"],
    )
    assert spec.file == ".github/workflows/playwright_saas_pr_trigger_monorepo.yml"
    assert spec.test_name == "Delete created organizations"
    assert "Delete orgs" in spec.error


def test_ci_job_spec_is_deterministic_not_flaky():
    # A CI-job failure must never read as flaky, or the agent "fixes" it with a
    # longer wait instead of repairing the step.
    spec = classify.ci_job_spec("merge-reports", workflow_path="w.yml", failing_steps=[])
    assert spec.deterministic is True
    assert spec.attempts == 1


def test_ci_job_spec_uses_the_leaf_of_a_nested_job_name():
    spec = classify.ci_job_spec(
        "Helm chart Integration Tests / agrn / Merge E2E Reports",
        workflow_path="w.yml",
        failing_steps=[],
    )
    assert spec.test_name == "Merge E2E Reports"


# ---------------------------------------------------------------------------
# Spec → source path
# ---------------------------------------------------------------------------


def test_suite_recovered_from_rootdir():
    root = "/__w/camunda/camunda/charts/camunda-platform-8.10/test/e2e/node_modules/@camunda/e2e-test-suite/dist/tests/SM-8.10"
    assert classify.suite_from_rootdir(root) == "SM-8.10"


def test_suite_recovered_from_saas_rootdir():
    # The SaaS run executes in the e2e repo checkout, which has no `dist` segment.
    root = "/home/runner/_work/c8-cross-component-e2e-tests/c8-cross-component-e2e-tests/tests/8.10"
    assert classify.suite_from_rootdir(root) == "8.10"


def test_suite_is_none_when_rootdir_unhelpful():
    assert classify.suite_from_rootdir("") is None
    assert classify.suite_from_rootdir("/some/other/dir") is None
    assert classify.suite_from_rootdir("/w/repo/tests/unit") is None


def test_compiled_basename_maps_to_source_path():
    assert (
        classify.source_spec_path("smoke-tests.spec.js", suite="SM-8.10")
        == "tests/SM-8.10/smoke-tests.spec.ts"
    )


def test_path_with_directories_is_left_alone():
    assert (
        classify.source_spec_path("tests/8.10/navigation.spec.ts", suite="8.10")
        == "tests/8.10/navigation.spec.ts"
    )


def test_basename_without_suite_is_returned_unchanged():
    assert classify.source_spec_path("smoke-tests.spec.js", suite=None) == (
        "smoke-tests.spec.ts"
    )


# ---------------------------------------------------------------------------
# Failing-spec extraction
# ---------------------------------------------------------------------------


def test_failing_spec_carries_retry_history_and_is_deterministic():
    # Run 30109387051: 3 attempts, all failed — the Keycloak error page case.
    report = _nested_report(
        [
            {
                "file": "smoke-tests.spec.js",
                "title": "Most Common Flow User Flow With All Apps",
                "ok": False,
                "tests": [
                    {
                        "projectName": "smoke-tests",
                        "results": [
                            {"status": "failed"},
                            {"status": "failed"},
                            {
                                "status": "failed",
                                "error": {
                                    "message": "\x1b[2mexpect(\x1b[22mlocator).toBeVisible failed"
                                },
                            },
                        ],
                    }
                ],
            }
        ]
    )
    specs = classify.failing_specs(report, suite="SM-8.10")
    assert len(specs) == 1
    spec = specs[0]
    assert spec.file == "tests/SM-8.10/smoke-tests.spec.ts"
    assert spec.attempts == 3
    assert spec.deterministic
    assert "\x1b" not in spec.error
    assert "toBeVisible failed" in spec.error


def test_spec_that_eventually_passed_is_not_deterministic():
    spec = classify.FailingSpec(
        file="f", test_name="t", statuses=["failed", "failed", "passed"]
    )
    assert not spec.deterministic


def test_spec_with_no_attempts_is_not_deterministic():
    assert not classify.FailingSpec(file="f", test_name="t").deterministic


def test_clean_error_truncates():
    assert len(classify.clean_error("x" * 5000)) == 600


# ---------------------------------------------------------------------------
# Fingerprints
# ---------------------------------------------------------------------------


def test_fingerprint_is_stable_and_short():
    fp = classify.spec_fingerprint("main", "sm-smoke-e2e", "a.spec.ts", "t")
    assert fp == classify.spec_fingerprint("main", "sm-smoke-e2e", "a.spec.ts", "t")
    assert len(fp) == 8


def test_fingerprint_differs_per_branch():
    assert classify.spec_fingerprint("main", "s", "f", "t") != classify.spec_fingerprint(
        "stable/8.9", "s", "f", "t"
    )


def test_job_fingerprint_ignores_nesting_prefix():
    a = classify.job_fingerprint("main", "helm-install", "A / B / install for install on gke - agrn")
    b = classify.job_fingerprint("main", "helm-install", "install for install on gke - agrn")
    assert a == b


# ---------------------------------------------------------------------------
# Blame
# ---------------------------------------------------------------------------


def test_human_pr_author_is_the_reviewer():
    # Run 30078726133.
    prs = [{"number": 58541, "merge_commit_sha": "sha1", "user": {"login": "slolatte"}}]
    blame = classify.resolve_blame(head_sha="sha1", prs=prs)
    assert blame.reviewer == "slolatte"
    assert blame.via == "pr-author"


def test_originating_pr_matches_merge_commit_not_list_order():
    # /commits/{sha}/pulls also returns unrelated open PRs containing the commit.
    prs = [
        {"number": 999, "merge_commit_sha": "other", "user": {"login": "someone"}},
        {"number": 58541, "merge_commit_sha": "sha1", "user": {"login": "slolatte"}},
    ]
    assert classify.originating_pr(prs, "sha1")["number"] == 58541


def test_backport_bot_resolves_to_original_author():
    # Run 30381967897: [Backport stable/8.7] … (#58938) authored by a bot.
    prs = [
        {
            "number": 58990,
            "merge_commit_sha": "sha1",
            "title": "[Backport stable/8.7] pg/sigsegv-stop-raft-role-preemptively (#58938)",
            "user": {"login": "monorepo-devops-automation[bot]"},
        }
    ]
    blame = classify.resolve_blame(
        head_sha="sha1",
        prs=prs,
        lookup_pr=lambda n: {"number": n, "user": {"login": "pihme"}},
    )
    assert blame.reviewer == "pihme"
    assert blame.pr_number == 58938
    assert blame.via == "backport-original"


def test_non_backport_bot_leaves_reviewer_unset_but_keeps_author():
    # Run 30109387051: renovate[bot], nothing to fall back to.
    prs = [
        {
            "number": 56547,
            "merge_commit_sha": "sha1",
            "title": "deps: Update camunda-platform Helm Chart to v15.0.0-alpha3 (main)",
            "user": {"login": "renovate[bot]"},
        }
    ]
    blame = classify.resolve_blame(head_sha="sha1", prs=prs)
    assert blame.reviewer is None
    assert blame.author == "renovate[bot]"
    assert blame.via == "bot-unresolved"


def test_backport_chain_ending_in_a_bot_stays_unresolved():
    prs = [
        {
            "number": 1,
            "merge_commit_sha": "sha1",
            "title": "[Backport stable/8.8] something (#2)",
            "user": {"login": "monorepo-release[bot]"},
        }
    ]
    blame = classify.resolve_blame(
        head_sha="sha1",
        prs=prs,
        lookup_pr=lambda n: {"user": {"login": "renovate[bot]"}},
    )
    assert blame.reviewer is None
    assert blame.via == "bot-unresolved"


def test_no_prs_yields_no_blame():
    blame = classify.resolve_blame(head_sha="sha1", prs=[])
    assert blame.reviewer is None and blame.via == "no-pr"


def test_is_bot():
    assert classify.is_bot("renovate[bot]")
    assert not classify.is_bot("slolatte")
    assert not classify.is_bot(None)


# ---------------------------------------------------------------------------
# Base ref normalisation
# ---------------------------------------------------------------------------


def test_plain_branch_is_unchanged():
    assert classify.normalise_base_ref("main") == "main"
    assert classify.normalise_base_ref("stable/8.9") == "stable/8.9"


def test_merge_queue_ref_collapses_to_its_base():
    # A merge_group run reports this shape. Left alone it would make every run's
    # fingerprints unique and silently defeat dedupe.
    assert (
        classify.normalise_base_ref("gh-readonly-queue/main/pr-59043-abc1234")
        == "main"
    )
    assert (
        classify.normalise_base_ref("gh-readonly-queue/stable/8.9/pr-123-deadbee")
        == "stable/8.9"
    )


def test_fully_qualified_ref_is_stripped():
    assert classify.normalise_base_ref("refs/heads/main") == "main"
    assert classify.normalise_base_ref("refs/heads/stable/8.7") == "stable/8.7"


def test_qualified_merge_queue_ref_handles_both_layers():
    assert (
        classify.normalise_base_ref("refs/heads/gh-readonly-queue/main/pr-1-abc")
        == "main"
    )


def test_branch_merely_containing_pr_is_not_truncated():
    assert classify.normalise_base_ref("feature/pr-review-tweaks") == (
        "feature/pr-review-tweaks"
    )


def test_empty_and_whitespace_are_safe():
    assert classify.normalise_base_ref("") == ""
    assert classify.normalise_base_ref("  main  ") == "main"


def test_normalised_ref_makes_fingerprints_stable_across_queue_runs():
    # The point of the normalisation: two merge-queue runs for different PRs targeting
    # the same branch must fingerprint identically, or dedupe never suppresses anything.
    a = classify.spec_fingerprint(
        classify.normalise_base_ref("gh-readonly-queue/main/pr-1-aaa"),
        "sm-smoke-e2e", "tests/SM-8.10/smoke-tests.spec.ts", "t",
    )
    b = classify.spec_fingerprint(
        classify.normalise_base_ref("gh-readonly-queue/main/pr-2-bbb"),
        "sm-smoke-e2e", "tests/SM-8.10/smoke-tests.spec.ts", "t",
    )
    direct = classify.spec_fingerprint(
        "main", "sm-smoke-e2e", "tests/SM-8.10/smoke-tests.spec.ts", "t"
    )
    assert a == b == direct

# ---------------------------------------------------------------------------
# Helm install verdict
# ---------------------------------------------------------------------------


def test_helm_scheduling_failure_is_infrastructure():
    # Shape taken from run 31103675147, where 0/39 nodes could take the pod.
    log = (
        "Warning  FailedScheduling  20m  default-scheduler  0/39 nodes are available: "
        "1 Insufficient cpu, 34 node(s) had untolerated taint(s), 4 node(s) didn't "
        "match Pod's node affinity/selector."
    )
    verdict, detail = classify.helm_install_verdict(log)
    assert verdict == classify.HELM_INFRASTRUCTURE
    assert detail == "FailedScheduling"


def test_helm_volume_attach_failure_is_infrastructure():
    log = 'Multi-Attach error for volume "pvc-8e24" Volume is already exclusively attached'
    verdict, _ = classify.helm_install_verdict(log)
    assert verdict == classify.HELM_INFRASTRUCTURE


def test_helm_values_error_is_actionable():
    log = "Error: INSTALLATION FAILED: values don't meet the specifications of the schema"
    verdict, detail = classify.helm_install_verdict(log)
    assert verdict == classify.HELM_ACTIONABLE
    assert detail == "INSTALLATION FAILED"


def test_helm_schema_error_without_install_prefix_is_actionable():
    # helm template and dry-run print the bare message; only install prefixes it with
    # INSTALLATION FAILED. Verified locally against camunda-platform-8.10.
    log = ("Error: values don't meet the specifications of the schema(s) in the "
           "following chart(s):\ncamunda-platform:\n- global.multitenancy.enabled: "
           "Invalid type. Expected: boolean, given: string")
    verdict, _ = classify.helm_install_verdict(log)
    assert verdict == classify.HELM_ACTIONABLE


def test_helm_crashloop_is_actionable():
    verdict, _ = classify.helm_install_verdict("identity  0/1  CrashLoopBackOff  5")
    assert verdict == classify.HELM_ACTIONABLE


def test_helm_chart_marker_wins_over_scheduling_noise():
    # A values bug can crash a pod the scheduler also complained about; the chart
    # reading is the one an agent can act on.
    log = "Warning FailedScheduling 0/39 nodes are available\nError: UPGRADE FAILED: template"
    verdict, detail = classify.helm_install_verdict(log)
    assert verdict == classify.HELM_ACTIONABLE
    assert detail == "UPGRADE FAILED"


def test_helm_unreadable_log_withholds():
    # gh failures yield an empty string; that must not read as "nothing was wrong".
    verdict, detail = classify.helm_install_verdict("")
    assert verdict == classify.HELM_INFRASTRUCTURE
    assert detail == "no chart-level failure signal"

