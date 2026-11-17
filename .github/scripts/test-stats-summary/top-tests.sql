-- Top tests per branch for the flaky and failed test summaries, with up to 3 runs per test.
--
-- Parameters:
--   @days         window: the last @days full days (UTC)
--   @test_status  "flaky" or "failure"
--   @triggers     build triggers to count, e.g. ["push", "merge_group", "pull_request"]
--   @branches     branches to report, e.g. ["main", "stable/8.10"]
--   @top          tests per branch
--
-- A run counts under its base branch: build_ref for a push, build_base_ref for a merge queue or
-- PR run. This is how the Grafana flaky tests dashboard and the CI alert rules group runs.
WITH test_rows AS (
  SELECT
    ts.test_class_name,
    COALESCE(ts.test_name, "") AS test_name,
    ts.build_id,
    ts.job_name,
    ts.report_time,
    REPLACE(IF(bs.build_trigger = "push", bs.build_ref, bs.build_base_ref), "refs/heads/", "") AS branch
  FROM `ci-30-162810.prod_ci_analytics.test_status_v1` ts
  JOIN `ci-30-162810.prod_ci_analytics.build_status_v2` bs
    ON ts.ci_url = bs.ci_url AND ts.build_id = bs.build_id AND ts.job_name = bs.job_name
  WHERE TIMESTAMP_SUB(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY), INTERVAL @days DAY) <= ts.report_time
    AND ts.report_time <= TIMESTAMP_TRUNC(CURRENT_TIMESTAMP, DAY)
    AND ts.ci_url = "https://github.com/camunda/camunda"
    AND ts.test_status = @test_status
    AND bs.build_trigger IN UNNEST(@triggers)
),
per_test AS (
  SELECT
    branch,
    test_class_name,
    test_name,
    COUNT(*) AS occurrences,
    COUNT(*) OVER (PARTITION BY branch) AS tests_on_branch
  FROM test_rows
  WHERE branch IN UNNEST(@branches)
  GROUP BY branch, test_class_name, test_name
  QUALIFY ROW_NUMBER() OVER (PARTITION BY branch ORDER BY COUNT(*) DESC, test_class_name, test_name) <= @top
),
-- One row per job, so a test row joined to a repeated build row does not repeat a link
jobs AS (
  SELECT branch, test_class_name, test_name, build_id, job_name, MAX(report_time) AS report_time
  FROM test_rows
  GROUP BY branch, test_class_name, test_name, build_id, job_name
)
SELECT
  p.branch,
  p.test_class_name,
  p.test_name,
  p.occurrences,
  p.tests_on_branch,
  ARRAY_AGG(
    STRUCT(
      SPLIT(j.build_id, "/")[SAFE_OFFSET(0)] AS run_id,
      SPLIT(j.build_id, "/")[SAFE_OFFSET(1)] AS run_attempt,
      j.job_name
    )
    ORDER BY j.report_time DESC
    LIMIT 3
  ) AS links
FROM per_test p
JOIN jobs j USING (branch, test_class_name, test_name)
GROUP BY p.branch, p.test_class_name, p.test_name, p.occurrences, p.tests_on_branch
ORDER BY p.branch, p.occurrences DESC, p.test_class_name, p.test_name
