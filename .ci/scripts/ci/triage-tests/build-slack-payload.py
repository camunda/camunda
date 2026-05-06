#!/usr/bin/env python3
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Camunda licenses this file to you under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with the License.
# You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
# CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
# language governing permissions and limitations under the License.

"""
Build the Slack Block Kit payload for the triage summary post.

Reads triage-report.json from the current directory and the following
environment variables (all set by the workflow step's env: block):
  TODAY, TOTAL, TEST_CODE, PRODUCT, TIMING, UNKNOWN,
  FIXED, FIX_TEXT, RUN_URL

Writes the JSON payload to stdout.
"""

import json
import os

report = json.load(open("triage-report.json"))
today = os.environ["TODAY"]
total = os.environ["TOTAL"]
test_code = os.environ["TEST_CODE"]
product = os.environ["PRODUCT"]
timing = os.environ["TIMING"]
unknown = os.environ["UNKNOWN"]
fixed = os.environ["FIXED"]
fix_text = os.environ["FIX_TEXT"]
run_url = os.environ["RUN_URL"]


def fmt_failures(hint, limit=8):
    items = [f for f in report["failures"] if f["root_cause_hint"] == hint][:limit]
    if not items:
        return "None :white_check_mark:"
    lines = []
    for f in items:
        leaf = f["name"].split(" > ")[-1]
        versions = ", ".join(f["versions"])
        reasons = "; ".join(f["root_cause_reasons"])
        lines.append(f"• `{leaf}` — {versions} — _{f['error_type']}_\n  › {reasons}")
    return "\n".join(lines)


if int(total) == 0:
    health = ":white_check_mark: All green — no failures"
elif int(product) > 0:
    health = ":red_circle: Product regression(s) detected"
elif int(unknown) > 0:
    health = ":large_orange_circle: Failures need investigation"
else:
    health = ":yellow_circle: Test code / timing issues only"

summary = (
    f"{health}\n"
    f"Total: *{total}* | "
    f":hammer_and_wrench: Test code: *{test_code}* | "
    f":red_circle: Product regression: *{product}* | "
    f":hourglass: Timing: *{timing}* | "
    f":question: Needs investigation: *{unknown}*\n"
    f"{fix_text}\n"
    f"<{run_url}|:mag: View triage run>"
)


def section(text):
    return {"type": "section", "text": {"type": "mrkdwn", "text": text}}


payload = {
    "channel": "prj-qa-sandbox",
    "text": f"C8 Orchestration Cluster Triage Report — {today}",
    "blocks": [
        {"type": "header", "text": {"type": "plain_text",
            "text": f":microscope: C8 Orchestration Cluster Triage — {today}"}},
        section(summary),
        {"type": "divider"},
        section("*:hammer_and_wrench: Test Code Issues* _(outdated locator, data isolation, wrong assertion)_\n"
                + fmt_failures("test_code")),
        {"type": "divider"},
        section("*:red_circle: Product Regressions* _(HTTP errors or assertion failures on a single version)_\n"
                + fmt_failures("product_regression")),
        {"type": "divider"},
        section("*:hourglass: Timing / Race Conditions* _(timeout or intermittent — needs human judgement)_\n"
                + fmt_failures("timing")),
        {"type": "divider"},
        section("*:question: Needs Investigation* _(conflicting or insufficient signals)_\n"
                + fmt_failures("needs_investigation")),
    ],
}
print(json.dumps(payload))
