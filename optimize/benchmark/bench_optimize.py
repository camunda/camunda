#!/usr/bin/env python3
"""
Optimize ProcessExecutionPlan end-to-end benchmark.

Phases:
  seed            Seed ES with Zeebe records via the Java generator
  create-reports  Create all 111 reports via Optimize REST API
  evaluate        Evaluate all reports and record latency
  all             Run all three phases in order (default)

Outputs:
  report_ids.json  Map of plan name -> Optimize report ID
  results.csv      Benchmark results sorted by p95_ms@M descending

Prerequisites:
  1. A running Optimize instance (default: http://localhost:8090)
  2. A running Elasticsearch instance (default: http://localhost:9200)
  3. For the seed phase, compile IT sources first:
       ./mvnw -pl optimize/backend test-compile -Dquickly

Example:
  # Full run
  python bench_optimize.py

  # Re-evaluate only (reports already created, data already seeded)
  python bench_optimize.py --phase evaluate

  # Seed S dataset only
  python bench_optimize.py --phase seed --dataset S
"""

import argparse
import csv
import json
import os
import statistics
import subprocess
import sys
import time
from pathlib import Path

import requests

# ── Constants ─────────────────────────────────────────────────────────────────

GENERATOR_CLASS = "io.camunda.optimize.test.generator.ZeebeDataGeneratorCli"

# The 6 process definition keys the generator creates by default
DEFAULT_PROCESS_KEYS = [
    "order-fulfillment",
    "invoice-processing",
    "loan-approval",
    "claim-processing",
    "customer-onboarding",
    "fraud-dispute-handling",
]

# Variable used for all VARIABLE groupBy / distributedBy / view payloads
VAR_NAME = "amount"
VAR_TYPE = "Double"

# Flow nodes used for process-part reports (from order-fulfillment.bpmn)
PROCESS_PART_KEY = "order-fulfillment"
PROCESS_PART_START = "startEvent_1"
PROCESS_PART_END = "endEvent_1"

DATE_UNIT = "automatic"

DATASET_CONFIGS = {
    "S": {"instances": 10_000,   "defs": 3},
    "M": {"instances": 100_000,  "defs": 6},
    "L": {"instances": 1_000_000, "defs": 6},
}

# Verdict thresholds (milliseconds / growth factor)
YELLOW_P95_MS   = 2_000
RED_P95_MS      = 5_000
YELLOW_GROWTH   = 10
RED_GROWTH      = 15

# Result types
MAP       = "MAP"
HYPER_MAP = "HYPER_MAP"
NUMBER    = "NUMBER"
RAW_DATA  = "RAW_DATA"

# Cost tiers
HIGH    = "HIGH"
MEDIUM  = "MEDIUM"
LOW     = "LOW"
TRIVIAL = "TRIVIAL"

# ── View / GroupBy / DistributedBy building blocks ───────────────────────────

def _view(entity, *props):
    return {"entity": entity, "properties": list(props)}

_DATE_VAL = {"unit": DATE_UNIT}
_VAR_VAL  = {"name": VAR_NAME, "type": VAR_TYPE}

# Views
FN_DUR   = _view("flowNode",       "duration")
FN_FREQ  = _view("flowNode",       "frequency")
PI_DUR   = _view("processInstance","duration")
PI_FREQ  = _view("processInstance","frequency")
PI_PCT   = _view("processInstance","percentage")
PI_RAW   = _view("processInstance","rawData")
UT_DUR   = _view("userTask",       "duration")
UT_FREQ  = _view("userTask",       "frequency")
INC_DUR  = _view("incident",       "duration")
INC_FREQ = _view("incident",       "frequency")
# Variable view: the server resolves the variable; we supply a concrete name
VAR_VIEW = _view("variable", _VAR_VAL)

# GroupBy
GB_FN       = {"type": "flowNodes"}
GB_FN_START = {"type": "startDate",    "value": _DATE_VAL}
GB_FN_END   = {"type": "endDate",      "value": _DATE_VAL}
GB_FN_DUR   = {"type": "duration"}
GB_PI_START = {"type": "startDate",    "value": _DATE_VAL}
GB_PI_END   = {"type": "endDate",      "value": _DATE_VAL}
GB_PI_RUN   = {"type": "runningDate",  "value": _DATE_VAL}
GB_PI_DUR   = {"type": "duration"}
GB_VAR      = {"type": "variable",     "value": _VAR_VAL}
GB_NONE     = {"type": "none"}
GB_ASSIGNEE = {"type": "assignee"}
GB_CAND     = {"type": "candidateGroup"}
GB_UT       = {"type": "userTasks"}
GB_UT_START = {"type": "startDate",    "value": _DATE_VAL}
GB_UT_END   = {"type": "endDate",      "value": _DATE_VAL}
GB_UT_DUR   = {"type": "duration"}

# DistributedBy
DB_NONE     = {"type": "none"}
DB_PROCESS  = {"type": "process"}
DB_FN       = {"type": "flowNode"}
DB_UT       = {"type": "userTask"}
DB_ASSIGNEE = {"type": "assignee"}
DB_CAND     = {"type": "candidateGroup"}
DB_VAR      = {"type": "variable",  "value": _VAR_VAL}
DB_PI_START = {"type": "startDate", "value": _DATE_VAL}
DB_PI_END   = {"type": "endDate",   "value": _DATE_VAL}


def _viz(result_type):
    return {"NUMBER": "number", "MAP": "bar", "HYPER_MAP": "bar", "RAW_DATA": "table"}[result_type]


# ── Plan table ────────────────────────────────────────────────────────────────
# Each entry: (view, groupBy, distributedBy, result_type, cost [, process_part])
# process_part=True means the report needs a processPart configuration block.

PLANS = {

    # ── Category 1: Flow Node Duration (11) ──────────────────────────────────
    "FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE":
        (FN_DUR, GB_FN,       DB_NONE,    MAP,       MEDIUM),
    "FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE_START_DATE":
        (FN_DUR, GB_FN_START, DB_NONE,    MAP,       LOW),
    "FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE_END_DATE":
        (FN_DUR, GB_FN_END,   DB_NONE,    MAP,       LOW),
    "FLOW_NODE_DURATION_GROUP_BY_VARIABLE":
        (FN_DUR, GB_VAR,      DB_NONE,    MAP,       HIGH),
    "FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE_BY_PROCESS":
        (FN_DUR, GB_FN,       DB_PROCESS, HYPER_MAP, MEDIUM),
    "FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE_START_DATE_BY_PROCESS":
        (FN_DUR, GB_FN_START, DB_PROCESS, HYPER_MAP, MEDIUM),
    "FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE_END_DATE_BY_PROCESS":
        (FN_DUR, GB_FN_END,   DB_PROCESS, HYPER_MAP, MEDIUM),
    "FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE_START_DATE_BY_FLOW_NODE":
        (FN_DUR, GB_FN_START, DB_FN,      HYPER_MAP, MEDIUM),
    "FLOW_NODE_DURATION_GROUP_BY_FLOW_NODE_END_DATE_BY_FLOW_NODE":
        (FN_DUR, GB_FN_END,   DB_FN,      HYPER_MAP, MEDIUM),
    "FLOW_NODE_DURATION_BY_VARIABLE_BY_PROCESS":
        (FN_DUR, GB_VAR,      DB_PROCESS, HYPER_MAP, HIGH),
    "FLOW_NODE_DURATION_BY_VARIABLE_BY_FLOW_NODE":
        (FN_DUR, GB_VAR,      DB_FN,      HYPER_MAP, HIGH),

    # ── Category 2: Flow Node Frequency (14) ─────────────────────────────────
    "FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE":
        (FN_FREQ, GB_FN,       DB_NONE,    MAP,       MEDIUM),
    "FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_START_DATE":
        (FN_FREQ, GB_FN_START, DB_NONE,    MAP,       LOW),
    "FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_END_DATE":
        (FN_FREQ, GB_FN_END,   DB_NONE,    MAP,       LOW),
    "FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_DURATION":
        (FN_FREQ, GB_FN_DUR,   DB_NONE,    MAP,       MEDIUM),
    "FLOW_NODE_FREQUENCY_GROUP_BY_VARIABLE":
        (FN_FREQ, GB_VAR,      DB_NONE,    MAP,       HIGH),
    "FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_BY_PROCESS":
        (FN_FREQ, GB_FN,       DB_PROCESS, HYPER_MAP, MEDIUM),
    "FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_START_DATE_BY_PROCESS":
        (FN_FREQ, GB_FN_START, DB_PROCESS, HYPER_MAP, MEDIUM),
    "FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_END_DATE_BY_PROCESS":
        (FN_FREQ, GB_FN_END,   DB_PROCESS, HYPER_MAP, MEDIUM),
    "FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_DURATION_BY_PROCESS":
        (FN_FREQ, GB_FN_DUR,   DB_PROCESS, HYPER_MAP, MEDIUM),
    "FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_START_DATE_BY_FLOW_NODE":
        (FN_FREQ, GB_FN_START, DB_FN,      HYPER_MAP, MEDIUM),
    "FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_END_DATE_BY_FLOW_NODE":
        (FN_FREQ, GB_FN_END,   DB_FN,      HYPER_MAP, MEDIUM),
    "FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_DURATION_BY_FLOW_NODE":
        (FN_FREQ, GB_FN_DUR,   DB_FN,      HYPER_MAP, MEDIUM),
    "FLOW_NODE_FREQUENCY_GROUP_BY_VARIABLE_BY_PROCESS":
        (FN_FREQ, GB_VAR,      DB_PROCESS, HYPER_MAP, HIGH),
    "FLOW_NODE_FREQUENCY_BY_VARIABLE_BY_FLOW_NODE":
        (FN_FREQ, GB_VAR,      DB_FN,      HYPER_MAP, HIGH),

    # ── Category 3: Incident Duration (2) ────────────────────────────────────
    "INCIDENT_DURATION_GROUP_BY_NONE":
        (INC_DUR, GB_NONE, DB_NONE, NUMBER, TRIVIAL),
    "INCIDENT_DURATION_GROUP_BY_FLOW_NODE":
        (INC_DUR, GB_FN,   DB_NONE, MAP,    MEDIUM),

    # ── Category 4: Incident Frequency (2) ───────────────────────────────────
    "INCIDENT_FREQUENCY_GROUP_BY_NONE":
        (INC_FREQ, GB_NONE, DB_NONE, NUMBER, TRIVIAL),
    "INCIDENT_FREQUENCY_GROUP_BY_FLOW_NODE":
        (INC_FREQ, GB_FN,   DB_NONE, MAP,    MEDIUM),

    # ── Category 5: Process Instance Duration (12) ───────────────────────────
    "PROCESS_INSTANCE_DURATION_GROUP_BY_NONE":
        (PI_DUR, GB_NONE,     DB_NONE,    NUMBER,    TRIVIAL),
    "PROCESS_INSTANCE_DURATION_GROUP_BY_START_DATE":
        (PI_DUR, GB_PI_START, DB_NONE,    MAP,       LOW),
    "PROCESS_INSTANCE_DURATION_GROUP_BY_END_DATE":
        (PI_DUR, GB_PI_END,   DB_NONE,    MAP,       LOW),
    "PROCESS_INSTANCE_DURATION_GROUP_BY_VARIABLE":
        (PI_DUR, GB_VAR,      DB_NONE,    MAP,       HIGH),
    "PROCESS_INSTANCE_DURATION_GROUP_BY_NONE_BY_PROCESS":
        (PI_DUR, GB_NONE,     DB_PROCESS, HYPER_MAP, MEDIUM),
    "PROCESS_INSTANCE_DURATION_GROUP_BY_START_DATE_BY_PROCESS":
        (PI_DUR, GB_PI_START, DB_PROCESS, HYPER_MAP, MEDIUM),
    "PROCESS_INSTANCE_DURATION_GROUP_BY_END_DATE_BY_PROCESS":
        (PI_DUR, GB_PI_END,   DB_PROCESS, HYPER_MAP, MEDIUM),
    "PROCESS_INSTANCE_DURATION_GROUP_BY_VARIABLE_BY_PROCESS":
        (PI_DUR, GB_VAR,      DB_PROCESS, HYPER_MAP, HIGH),
    "PROCESS_INSTANCE_DURATION_GROUP_BY_VARIABLE_BY_START_DATE":
        (PI_DUR, GB_VAR,      DB_PI_START, HYPER_MAP, HIGH),
    "PROCESS_INSTANCE_DURATION_GROUP_BY_VARIABLE_BY_END_DATE":
        (PI_DUR, GB_VAR,      DB_PI_END,   HYPER_MAP, HIGH),
    "PROCESS_INSTANCE_DURATION_GROUP_BY_START_DATE_BY_VARIABLE":
        (PI_DUR, GB_PI_START, DB_VAR,      HYPER_MAP, HIGH),
    "PROCESS_INSTANCE_DURATION_GROUP_BY_END_DATE_BY_VARIABLE":
        (PI_DUR, GB_PI_END,   DB_VAR,      HYPER_MAP, HIGH),

    # ── Category 6: Process Instance Duration — Process Part (8) ─────────────
    "PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_NONE":
        (PI_DUR, GB_NONE,     DB_NONE,     NUMBER,    TRIVIAL, True),
    "PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_START_DATE":
        (PI_DUR, GB_PI_START, DB_NONE,     MAP,       LOW,     True),
    "PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_END_DATE":
        (PI_DUR, GB_PI_END,   DB_NONE,     MAP,       LOW,     True),
    "PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_VARIABLE":
        (PI_DUR, GB_VAR,      DB_NONE,     MAP,       HIGH,    True),
    "PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_VARIABLE_BY_START_DATE":
        (PI_DUR, GB_VAR,      DB_PI_START, HYPER_MAP, HIGH,    True),
    "PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_VARIABLE_BY_END_DATE":
        (PI_DUR, GB_VAR,      DB_PI_END,   HYPER_MAP, HIGH,    True),
    "PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_START_DATE_BY_VARIABLE":
        (PI_DUR, GB_PI_START, DB_VAR,      HYPER_MAP, HIGH,    True),
    "PROCESS_INSTANCE_DURATION_ON_PROCESS_PART_GROUP_BY_END_DATE_BY_VARIABLE":
        (PI_DUR, GB_PI_END,   DB_VAR,      HYPER_MAP, HIGH,    True),

    # ── Category 7: Process Instance Frequency (16) ──────────────────────────
    "PROCESS_INSTANCE_FREQUENCY_GROUP_BY_NONE":
        (PI_FREQ, GB_NONE,     DB_NONE,    NUMBER,    TRIVIAL),
    "PROCESS_INSTANCE_FREQUENCY_GROUP_BY_START_DATE":
        (PI_FREQ, GB_PI_START, DB_NONE,    MAP,       LOW),
    "PROCESS_INSTANCE_FREQUENCY_GROUP_BY_END_DATE":
        (PI_FREQ, GB_PI_END,   DB_NONE,    MAP,       LOW),
    "PROCESS_INSTANCE_FREQUENCY_GROUP_BY_RUNNING_DATE":
        (PI_FREQ, GB_PI_RUN,   DB_NONE,    MAP,       MEDIUM),
    "PROCESS_INSTANCE_FREQUENCY_GROUP_BY_DURATION":
        (PI_FREQ, GB_PI_DUR,   DB_NONE,    MAP,       MEDIUM),
    "PROCESS_INSTANCE_FREQUENCY_GROUP_BY_VARIABLE":
        (PI_FREQ, GB_VAR,      DB_NONE,    MAP,       HIGH),
    "PROCESS_INSTANCE_FREQUENCY_GROUP_BY_NONE_BY_PROCESS":
        (PI_FREQ, GB_NONE,     DB_PROCESS, HYPER_MAP, MEDIUM),
    "PROCESS_INSTANCE_FREQUENCY_GROUP_BY_START_DATE_BY_PROCESS":
        (PI_FREQ, GB_PI_START, DB_PROCESS, HYPER_MAP, MEDIUM),
    "PROCESS_INSTANCE_FREQUENCY_GROUP_BY_END_DATE_BY_PROCESS":
        (PI_FREQ, GB_PI_END,   DB_PROCESS, HYPER_MAP, MEDIUM),
    "PROCESS_INSTANCE_FREQUENCY_GROUP_BY_RUNNING_DATE_BY_PROCESS":
        (PI_FREQ, GB_PI_RUN,   DB_PROCESS, HYPER_MAP, MEDIUM),
    "PROCESS_INSTANCE_FREQUENCY_GROUP_BY_DURATION_BY_PROCESS":
        (PI_FREQ, GB_PI_DUR,   DB_PROCESS, HYPER_MAP, MEDIUM),
    "PROCESS_INSTANCE_FREQUENCY_GROUP_BY_VARIABLE_BY_PROCESS":
        (PI_FREQ, GB_VAR,      DB_PROCESS, HYPER_MAP, HIGH),
    "PROCESS_INSTANCE_FREQUENCY_GROUP_BY_VARIABLE_BY_START_DATE":
        (PI_FREQ, GB_VAR,      DB_PI_START, HYPER_MAP, HIGH),
    "PROCESS_INSTANCE_FREQUENCY_GROUP_BY_VARIABLE_BY_END_DATE":
        (PI_FREQ, GB_VAR,      DB_PI_END,   HYPER_MAP, HIGH),
    "PROCESS_INSTANCE_FREQUENCY_GROUP_BY_START_DATE_BY_VARIABLE":
        (PI_FREQ, GB_PI_START, DB_VAR,      HYPER_MAP, HIGH),
    "PROCESS_INSTANCE_FREQUENCY_GROUP_BY_END_DATE_BY_VARIABLE":
        (PI_FREQ, GB_PI_END,   DB_VAR,      HYPER_MAP, HIGH),

    # ── Category 8: Process Instance Percentage (1) ──────────────────────────
    "PROCESS_INSTANCE_PERCENTAGE_GROUP_BY_NONE":
        (PI_PCT, GB_NONE, DB_NONE, NUMBER, TRIVIAL),

    # ── Category 9: Raw Data (1) ──────────────────────────────────────────────
    "PROCESS_RAW_PROCESS_INSTANCE_DATA_GROUP_BY_NONE":
        (PI_RAW, GB_NONE, DB_NONE, RAW_DATA, MEDIUM),

    # ── Category 10: User Task Duration (20) ─────────────────────────────────
    "PROCESS_USER_TASK_DURATION_GROUP_BY_TASK":
        (UT_DUR, GB_UT,       DB_NONE,    MAP,       MEDIUM),
    "PROCESS_USER_TASK_DURATION_GROUP_BY_ASSIGNEE":
        (UT_DUR, GB_ASSIGNEE, DB_NONE,    MAP,       MEDIUM),
    "PROCESS_USER_TASK_DURATION_GROUP_BY_CANDIDATE_GROUP":
        (UT_DUR, GB_CAND,     DB_NONE,    MAP,       MEDIUM),
    "PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_START_DATE":
        (UT_DUR, GB_UT_START, DB_NONE,    MAP,       LOW),
    "PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_END_DATE":
        (UT_DUR, GB_UT_END,   DB_NONE,    MAP,       LOW),
    "PROCESS_USER_TASK_DURATION_GROUP_BY_TASK_GROUP_BY_PROCESS":
        (UT_DUR, GB_UT,       DB_PROCESS, HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_DURATION_GROUP_BY_TASK_GROUP_BY_ASSIGNEE":
        (UT_DUR, GB_UT,       DB_ASSIGNEE, HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_DURATION_GROUP_BY_TASK_GROUP_BY_CANDIDATE_GROUP":
        (UT_DUR, GB_UT,       DB_CAND,    HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_DURATION_GROUP_BY_ASSIGNEE_BY_PROCESS":
        (UT_DUR, GB_ASSIGNEE, DB_PROCESS, HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_DURATION_GROUP_BY_ASSIGNEE_BY_TASK":
        (UT_DUR, GB_ASSIGNEE, DB_UT,      HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_DURATION_GROUP_BY_CANDIDATE_GROUP_BY_PROCESS":
        (UT_DUR, GB_CAND,     DB_PROCESS, HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_DURATION_GROUP_BY_CANDIDATE_GROUP_BY_TASK":
        (UT_DUR, GB_CAND,     DB_UT,      HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_START_DATE_BY_PROCESS":
        (UT_DUR, GB_UT_START, DB_PROCESS, HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_END_DATE_BY_PROCESS":
        (UT_DUR, GB_UT_END,   DB_PROCESS, HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_START_DATE_BY_TASK":
        (UT_DUR, GB_UT_START, DB_UT,      HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_END_DATE_BY_TASK":
        (UT_DUR, GB_UT_END,   DB_UT,      HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_START_DATE_BY_ASSIGNEE":
        (UT_DUR, GB_UT_START, DB_ASSIGNEE, HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_END_DATE_BY_ASSIGNEE":
        (UT_DUR, GB_UT_END,   DB_ASSIGNEE, HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_START_DATE_BY_CANDIDATE_GROUP":
        (UT_DUR, GB_UT_START, DB_CAND,    HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_DURATION_GROUP_BY_USER_TASK_END_DATE_BY_CANDIDATE_GROUP":
        (UT_DUR, GB_UT_END,   DB_CAND,    HYPER_MAP, MEDIUM),

    # ── Category 11: User Task Frequency (23) ────────────────────────────────
    "PROCESS_USER_TASK_FREQUENCY_GROUP_BY_TASK":
        (UT_FREQ, GB_UT,       DB_NONE,    MAP,       MEDIUM),
    "PROCESS_USER_TASK_FREQUENCY_GROUP_BY_ASSIGNEE":
        (UT_FREQ, GB_ASSIGNEE, DB_NONE,    MAP,       MEDIUM),
    "PROCESS_USER_TASK_FREQUENCY_GROUP_BY_CANDIDATE_GROUP":
        (UT_FREQ, GB_CAND,     DB_NONE,    MAP,       MEDIUM),
    "PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_START_DATE":
        (UT_FREQ, GB_UT_START, DB_NONE,    MAP,       LOW),
    "PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_END_DATE":
        (UT_FREQ, GB_UT_END,   DB_NONE,    MAP,       LOW),
    "PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_DURATION":
        (UT_FREQ, GB_UT_DUR,   DB_NONE,    MAP,       MEDIUM),
    "PROCESS_USER_TASK_FREQUENCY_GROUP_BY_TASK_BY_PROCESS":
        (UT_FREQ, GB_UT,       DB_PROCESS, HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_FREQUENCY_GROUP_BY_TASK_BY_ASSIGNEE":
        (UT_FREQ, GB_UT,       DB_ASSIGNEE, HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_FREQUENCY_GROUP_BY_TASK_BY_CANDIDATE_GROUP":
        (UT_FREQ, GB_UT,       DB_CAND,    HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_FREQUENCY_GROUP_BY_ASSIGNEE_DISTRIBUTE_BY_PROCESS":
        (UT_FREQ, GB_ASSIGNEE, DB_PROCESS, HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_FREQUENCY_GROUP_BY_ASSIGNEE_DISTRIBUTE_BY_TASK":
        (UT_FREQ, GB_ASSIGNEE, DB_UT,      HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_FREQUENCY_GROUP_BY_CANDIDATE_DISTRIBUTE_BY_PROCESS":
        (UT_FREQ, GB_CAND,     DB_PROCESS, HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_FREQUENCY_GROUP_BY_CANDIDATE_DISTRIBUTE_BY_TASK":
        (UT_FREQ, GB_CAND,     DB_UT,      HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_START_DATE_BY_PROCESS":
        (UT_FREQ, GB_UT_START, DB_PROCESS, HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_END_DATE_BY_PROCESS":
        (UT_FREQ, GB_UT_END,   DB_PROCESS, HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_START_DATE_BY_TASK":
        (UT_FREQ, GB_UT_START, DB_UT,      HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_END_DATE_BY_TASK":
        (UT_FREQ, GB_UT_END,   DB_UT,      HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_DURATION_BY_PROCESS":
        (UT_FREQ, GB_UT_DUR,   DB_PROCESS, HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_DURATION_BY_USER_TASK":
        (UT_FREQ, GB_UT_DUR,   DB_UT,      HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_START_DATE_BY_ASSIGNEE":
        (UT_FREQ, GB_UT_START, DB_ASSIGNEE, HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_END_DATE_BY_ASSIGNEE":
        (UT_FREQ, GB_UT_END,   DB_ASSIGNEE, HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_START_DATE_BY_CANDIDATE_GROUP":
        (UT_FREQ, GB_UT_START, DB_CAND,    HYPER_MAP, MEDIUM),
    "PROCESS_USER_TASK_FREQUENCY_GROUP_BY_USER_TASK_END_DATE_BY_CANDIDATE_GROUP":
        (UT_FREQ, GB_UT_END,   DB_CAND,    HYPER_MAP, MEDIUM),

    # ── Category 12: Variable Analysis (1) ───────────────────────────────────
    "PROCESS_VARIABLE_GROUP_BY_NONE":
        (VAR_VIEW, GB_NONE, DB_NONE, NUMBER, TRIVIAL),
}

# ── Auth / Session ────────────────────────────────────────────────────────────

def make_session(
    optimize_url: str,
    username: str,
    password: str,
    keycloak_url: str = "",
    token: str = "",
) -> requests.Session:
    """
    Return an authenticated requests.Session for the Optimize API.

    Auth strategy (first that succeeds):
      1. --token  supplied directly — set as X-Optimize-Authorization_0 cookie
      2. --keycloak-url  — fetch token via Keycloak password grant, set as cookie
      3. POST /api/authentication  — classic embedded-mode username/password login
    """
    session = requests.Session()

    # ── Strategy 1: bare token provided by caller ──────────────────────────
    if token:
        session.cookies.set("X-Optimize-Authorization_0", token)
        print("[auth] Using supplied token")
        return session

    # ── Strategy 2: Keycloak password grant ───────────────────────────────
    if keycloak_url:
        token = _keycloak_token(keycloak_url, username, password)
        _set_auth_cookie(session, token)
        print(f"[auth] Keycloak token obtained for {username}")
        return session

    # ── Strategy 3: classic POST /api/authentication (embedded / test mode) ─
    resp = session.post(
        f"{optimize_url}/api/authentication",
        json={"username": username, "password": password},
        timeout=30,
    )
    if resp.status_code in (200, 204):
        print(f"[auth] Logged in as {username} (classic auth)")
        return session

    # Classic auth not available — give a helpful error
    print(
        f"[ERROR] Login failed ({resp.status_code}).\n"
        "If Optimize is running in CCSM (self-managed with Identity/Keycloak) mode, "
        "use one of:\n"
        "  --keycloak-url http://localhost:18080/auth/realms/camunda-platform"
        "/protocol/openid-connect/token\n"
        "  --token <your-keycloak-access-token>",
        file=sys.stderr,
    )
    sys.exit(1)


def _keycloak_token(token_url: str, username: str, password: str) -> str:
    """Obtain an access token from Keycloak using the resource-owner password grant."""
    resp = requests.post(
        token_url,
        data={
            "grant_type": "password",
            "client_id":  "optimize",
            "username":   username,
            "password":   password,
            "scope":      "openid",
        },
        timeout=30,
    )
    if not resp.ok:
        print(
            f"[ERROR] Keycloak token request failed ({resp.status_code}): {resp.text[:300]}",
            file=sys.stderr,
        )
        sys.exit(1)
    return resp.json()["access_token"]


def _set_auth_cookie(session: requests.Session, token: str):
    """
    Chunk the token into ≤4096-byte cookies matching Optimize's naming scheme:
    X-Optimize-Authorization_0, X-Optimize-Authorization_1, …
    """
    chunk = 4096
    for i, start in enumerate(range(0, len(token), chunk)):
        session.cookies.set(
            f"X-Optimize-Authorization_{i}",
            token[start : start + chunk],
        )


# ── Report payload builder ────────────────────────────────────────────────────

def build_report_payload(plan_name: str, plan_cfg: tuple, process_keys: list) -> dict:
    """Construct the POST /api/report/process/single request body for one plan."""
    view, group_by, distributed_by, result_type, cost, *rest = plan_cfg
    is_process_part = bool(rest and rest[0])

    definitions = (
        [{"key": PROCESS_PART_KEY, "versions": ["all"]}]
        if is_process_part
        else [{"key": k, "versions": ["all"]} for k in process_keys]
    )

    configuration = {}
    if is_process_part:
        configuration["processPart"] = {
            "start": PROCESS_PART_START,
            "end":   PROCESS_PART_END,
        }

    return {
        "name": f"BENCH_{plan_name}",
        "combined": False,
        "reportType": "process",
        "data": {
            "definitions":   definitions,
            "view":          view,
            "groupBy":       group_by,
            "distributedBy": distributed_by,
            "visualization": _viz(result_type),
            "filter":        [],
            "configuration": configuration,
        },
    }


# ── Phase: create-reports ─────────────────────────────────────────────────────

def create_reports(
    optimize_url: str,
    session: requests.Session,
    process_keys: list,
    ids_file: Path,
) -> dict:
    """
    Create one saved report per plan.  Returns {plan_name: report_id}.
    Skips plans whose IDs already exist in ids_file (idempotent).
    """
    existing: dict = {}
    if ids_file.exists():
        existing = json.loads(ids_file.read_text())
        print(f"[create] Loaded {len(existing)} existing report IDs from {ids_file}")

    report_ids = dict(existing)
    plans_to_create = [p for p in PLANS if p not in report_ids]
    print(f"[create] Creating {len(plans_to_create)} reports "
          f"({len(existing)} already exist) …")

    for plan_name in plans_to_create:
        payload = build_report_payload(plan_name, PLANS[plan_name], process_keys)
        resp = session.post(
            f"{optimize_url}/api/report/process/single",
            json=payload,
            timeout=30,
        )
        if resp.status_code not in (200, 201):
            print(f"[WARN] {plan_name}: creation failed ({resp.status_code}) — "
                  f"{resp.text[:200]}")
            continue
        report_id = resp.json().get("id") or resp.json().get("reportId")
        if not report_id:
            print(f"[WARN] {plan_name}: no id in response: {resp.text[:200]}")
            continue
        report_ids[plan_name] = report_id
        ids_file.write_text(json.dumps(report_ids, indent=2))
        print(f"[create] {plan_name} -> {report_id}")

    print(f"[create] Done. {len(report_ids)} reports ready.")
    return report_ids


# ── Phase: seed ───────────────────────────────────────────────────────────────

def seed_dataset(
    dataset: str,
    es_host: str,
    es_port: int,
    es_user: str,
    es_password: str,
    repo_root: Path,
):
    """Compile IT sources (if needed) then run ZeebeDataGeneratorCli."""
    cfg = DATASET_CONFIGS[dataset]
    instances = cfg["instances"]
    defs      = cfg["defs"]

    print(f"\n[seed] Dataset {dataset}: {instances:,} instances, {defs} process defs")

    # Build args string for exec:java
    generator_args = (
        f"--instances {instances} "
        f"--defs {defs} "
        f"--host {es_host} "
        f"--port {es_port} "
        f"--batch-size 1000 "
        f"--update-rate 0.25"
    )
    if es_user:
        generator_args += f" --username {es_user} --password {es_password}"

    mvnw = str(repo_root / "mvnw")
    # Run Maven from optimize/backend directly — the root pom excludes the
    # optimize module when -Dquickly is set, so -pl optimize/backend fails.
    backend_dir = repo_root / "optimize" / "backend"

    # Step 1: compile IT sources so the generator class is on the classpath
    print("[seed] Compiling IT sources …")
    subprocess.run(
        [mvnw, "test-compile", "-Dquickly", "-T1C"],
        cwd=backend_dir,
        check=True,
    )

    # Step 2: run the generator
    print(f"[seed] Running generator ({instances:,} instances) …")
    start = time.time()
    subprocess.run(
        [
            mvnw,
            "exec:java",
            f"-Dexec.mainClass={GENERATOR_CLASS}",
            "-Dexec.classpathScope=test",
            f"-Dexec.args={generator_args}",
        ],
        cwd=backend_dir,
        check=True,
    )
    elapsed = time.time() - start
    print(f"[seed] Dataset {dataset} seeded in {elapsed:.0f}s")


# ── ES flush + force-merge ────────────────────────────────────────────────────

def es_flush_and_merge(es_url: str, es_auth: tuple | None):
    """Flush and force-merge all indices to avoid cache bleed between datasets."""
    kwargs = {"auth": es_auth} if es_auth else {}
    print("[es] Flushing indices …")
    requests.post(f"{es_url}/_flush", timeout=120, **kwargs).raise_for_status()
    print("[es] Force-merging indices (max_num_segments=1) …")
    requests.post(
        f"{es_url}/_forcemerge",
        params={"max_num_segments": 1},
        timeout=600,
        **kwargs,
    ).raise_for_status()
    print("[es] Flush + force-merge complete")


# ── Wait for Optimize import ──────────────────────────────────────────────────

def wait_for_import(
    optimize_url: str,
    session: requests.Session,
    wait_secs: int,
    poll_interval: int = 10,
):
    """
    Poll the Optimize import progress endpoint until idle, or fall back to a
    fixed sleep if the endpoint is unavailable.
    """
    print(f"[import] Waiting up to {wait_secs}s for Optimize to finish importing …")
    deadline = time.time() + wait_secs

    while time.time() < deadline:
        try:
            resp = session.get(
                f"{optimize_url}/api/status/import-progress",
                timeout=10,
            )
            if resp.status_code == 200:
                body = resp.json()
                # The endpoint returns a map of importer -> % complete (0-100)
                values = list(body.values()) if isinstance(body, dict) else []
                if values and all(v >= 100 for v in values):
                    print("[import] All importers at 100% — proceeding")
                    return
                pct = sum(values) / len(values) if values else 0
                print(f"[import] Progress: {pct:.0f}% avg — waiting …")
            else:
                # Endpoint not available; fall through to timed wait
                break
        except requests.RequestException:
            break
        time.sleep(poll_interval)

    # Fallback: just sleep the full wait time
    remaining = max(0, int(deadline - time.time()))
    if remaining > 0:
        print(f"[import] Progress endpoint unavailable — sleeping {remaining}s …")
        time.sleep(remaining)
    print("[import] Import wait complete")


# ── Phase: evaluate ───────────────────────────────────────────────────────────

def evaluate_once(
    report_id: str,
    optimize_url: str,
    session: requests.Session,
) -> tuple[float, int, int]:
    """
    Call POST /api/report/{id}/evaluate once.
    Returns (wall_ms, http_status, es_took_ms).
    es_took_ms is -1 if not present in the response.
    """
    t0 = time.perf_counter()
    resp = session.post(
        f"{optimize_url}/api/report/{report_id}/evaluate",
        json={},
        timeout=120,
    )
    wall_ms = (time.perf_counter() - t0) * 1000

    es_took = -1
    if resp.ok:
        try:
            body = resp.json()
            # Try common paths where ES took might surface
            es_took = (
                body.get("took")
                or body.get("data", {}).get("took")
                or -1
            )
        except Exception:
            pass

    return wall_ms, resp.status_code, int(es_took)


def evaluate_plan(
    plan_name: str,
    report_id: str,
    optimize_url: str,
    session: requests.Session,
    n_warmup: int,
    n_measured: int,
) -> dict:
    """
    Run n_warmup discarded calls then n_measured timed calls.
    Returns a result dict with p50_ms, p95_ms, es_took_ms, status.
    """
    # Warmup
    for _ in range(n_warmup):
        evaluate_once(report_id, optimize_url, session)

    # Measured
    times = []
    es_tooks = []
    last_status = 0
    for _ in range(n_measured):
        wall_ms, status, es_took = evaluate_once(report_id, optimize_url, session)
        times.append(wall_ms)
        if es_took >= 0:
            es_tooks.append(es_took)
        last_status = status

    times.sort()
    p50 = statistics.median(times)
    p95 = times[int(len(times) * 0.95)] if len(times) >= 20 else max(times)
    es_avg = int(statistics.mean(es_tooks)) if es_tooks else -1

    return {
        "plan":       plan_name,
        "p50_ms":     round(p50),
        "p95_ms":     round(p95),
        "es_took_ms": es_avg,
        "status":     last_status,
    }


# ── Verdict ───────────────────────────────────────────────────────────────────

def compute_verdict(p95_ms: int, growth_factor: float | None) -> str:
    if p95_ms > RED_P95_MS:
        return "RED"
    if growth_factor is not None and growth_factor > RED_GROWTH:
        return "RED"
    if p95_ms > YELLOW_P95_MS:
        return "YELLOW"
    if growth_factor is not None and growth_factor > YELLOW_GROWTH:
        return "YELLOW"
    return "GREEN"


# ── CSV helpers ───────────────────────────────────────────────────────────────

CSV_FIELDS = ["plan", "dataset", "p50_ms", "p95_ms", "es_took_ms", "status", "cost", "verdict"]


def load_csv(path: Path) -> list[dict]:
    if not path.exists():
        return []
    with path.open() as f:
        return list(csv.DictReader(f))


def save_csv(path: Path, rows: list[dict]):
    with path.open("w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=CSV_FIELDS)
        w.writeheader()
        w.writerows(rows)


def already_done(rows: list[dict], plan: str, dataset: str) -> bool:
    return any(r["plan"] == plan and r["dataset"] == dataset for r in rows)


def print_summary(rows: list[dict]):
    """Print results sorted by p95_ms at M descending."""
    m_rows = [r for r in rows if r["dataset"] == "M"]
    if not m_rows:
        print("\n[summary] No M-dataset results yet.")
        return

    m_rows.sort(key=lambda r: int(r["p95_ms"]), reverse=True)
    print(f"\n{'plan':<70} {'p50':>7} {'p95':>7} {'verdict':<8} {'cost'}")
    print("-" * 105)
    for r in m_rows:
        flag = {"RED": "🔴", "YELLOW": "🟡", "GREEN": "🟢"}.get(r["verdict"], "  ")
        print(
            f"{r['plan']:<70} {r['p50_ms']:>6}ms {r['p95_ms']:>6}ms "
            f" {flag} {r['verdict']:<6}  {r['cost']}"
        )


# ── Discover process keys from Optimize ──────────────────────────────────────

def discover_process_keys(optimize_url: str, session: requests.Session) -> list[str]:
    """
    Query Optimize for all imported process definitions and return their keys.
    Falls back to the static DEFAULT_PROCESS_KEYS list if the endpoint fails.
    """
    try:
        resp = session.get(
            f"{optimize_url}/api/process-definition",
            timeout=30,
        )
        if resp.ok:
            defs = resp.json()
            keys = list({d["key"] for d in defs if "key" in d})
            if keys:
                print(f"[discover] Found {len(keys)} process definition(s): {keys}")
                return keys
    except Exception as exc:
        print(f"[discover] Could not query process definitions: {exc}")

    print(f"[discover] Falling back to default process keys: {DEFAULT_PROCESS_KEYS}")
    return DEFAULT_PROCESS_KEYS


# ── Main orchestration ────────────────────────────────────────────────────────

def run(args):
    optimize_url = args.optimize_url.rstrip("/")
    es_url       = args.es_url.rstrip("/")
    repo_root    = Path(args.repo_root)
    ids_file     = Path(args.ids_file)
    csv_path     = Path(args.output)
    datasets     = [args.dataset] if args.dataset != "all" else ["S", "M", "L"]
    phase        = args.phase

    username, password = args.auth.split(":", 1)
    es_auth = (args.es_user, args.es_password) if args.es_user else None

    # ── Phase: seed ───────────────────────────────────────────────────────────
    if phase in ("seed", "all"):
        for ds in datasets:
            seed_dataset(ds, args.es_host, args.es_port, args.es_user or "",
                         args.es_password or "", repo_root)
            es_flush_and_merge(es_url, es_auth)

        session = make_session(optimize_url, username, password, args.keycloak_url, args.token)
        wait_for_import(optimize_url, session, args.import_wait)

        if phase == "seed":
            return

    # ── Phase: create-reports ─────────────────────────────────────────────────
    if phase in ("create-reports", "all"):
        session = make_session(optimize_url, username, password, args.keycloak_url, args.token)
        process_keys = discover_process_keys(optimize_url, session)
        create_reports(optimize_url, session, process_keys, ids_file)

        if phase == "create-reports":
            return

    # ── Phase: evaluate ───────────────────────────────────────────────────────
    if phase in ("evaluate", "all"):
        if not ids_file.exists():
            print(f"[ERROR] {ids_file} not found — run --phase create-reports first.",
                  file=sys.stderr)
            sys.exit(1)

        report_ids: dict = json.loads(ids_file.read_text())
        rows = load_csv(csv_path)
        session = make_session(optimize_url, username, password, args.keycloak_url, args.token)

        total = len(PLANS) * len(datasets)
        done  = 0

        for ds in datasets:
            print(f"\n{'='*60}")
            print(f"  Dataset {ds}  ({DATASET_CONFIGS[ds]['instances']:,} instances)")
            print(f"{'='*60}")

            for plan_name, plan_cfg in PLANS.items():
                if already_done(rows, plan_name, ds):
                    done += 1
                    print(f"[skip] {plan_name} / {ds} (already in CSV)")
                    continue

                report_id = report_ids.get(plan_name)
                if not report_id:
                    print(f"[skip] {plan_name} — no report ID, skipping")
                    done += 1
                    continue

                print(f"[eval {done+1}/{total}] {plan_name} / {ds} …", end=" ", flush=True)
                result = evaluate_plan(
                    plan_name, report_id, optimize_url, session,
                    args.warmups, args.measured,
                )

                _, _, _, result_type, cost, *_ = plan_cfg
                result["dataset"] = ds
                result["cost"]    = cost

                # Compute growth factor vs M if L result and M exists
                growth = None
                if ds == "L":
                    m_row = next(
                        (r for r in rows if r["plan"] == plan_name and r["dataset"] == "M"),
                        None,
                    )
                    if m_row and int(m_row["p95_ms"]) > 0:
                        growth = result["p95_ms"] / int(m_row["p95_ms"])

                result["verdict"] = compute_verdict(result["p95_ms"], growth)
                rows.append(result)
                save_csv(csv_path, rows)
                done += 1

                growth_str = f"  growth={growth:.1f}x" if growth else ""
                print(
                    f"p50={result['p50_ms']}ms  p95={result['p95_ms']}ms  "
                    f"status={result['status']}  {result['verdict']}{growth_str}"
                )

        print_summary(rows)
        print(f"\n[done] Results written to {csv_path}")


# ── CLI ───────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Benchmark all Optimize ProcessExecutionPlan entries end-to-end.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("--phase", choices=["all", "seed", "create-reports", "evaluate"],
                        default="all", help="Which phase to run")
    parser.add_argument("--dataset", choices=["S", "M", "L", "all"],
                        default="all", help="Dataset size(s) to seed/evaluate")
    parser.add_argument("--optimize-url", default="http://localhost:8090",
                        help="Base URL of the Optimize instance")
    parser.add_argument("--es-url", default="http://localhost:9200",
                        help="Base URL of Elasticsearch (for flush/merge)")
    parser.add_argument("--es-host", default="localhost",
                        help="ES host passed to the Java generator")
    parser.add_argument("--es-port", type=int, default=9200,
                        help="ES port passed to the Java generator")
    parser.add_argument("--es-user", default="",
                        help="ES basic-auth username (optional)")
    parser.add_argument("--es-password", default="",
                        help="ES basic-auth password (optional)")
    parser.add_argument("--auth", default="demo:demo",
                        help="Optimize credentials as user:password")
    parser.add_argument(
        "--keycloak-url", default="",
        help=(
            "Keycloak token endpoint for CCSM mode, e.g. "
            "http://localhost:18080/auth/realms/camunda-platform"
            "/protocol/openid-connect/token"
        ),
    )
    parser.add_argument(
        "--token", default="",
        help="Pre-obtained Keycloak/Identity access token (skips login entirely)",
    )
    parser.add_argument("--repo-root", default=str(Path(__file__).parent.parent.parent),
                        help="Path to the camunda monorepo root (for mvnw)")
    parser.add_argument("--ids-file", default="report_ids.json",
                        help="Path to persist report IDs between runs")
    parser.add_argument("--output", default="results.csv",
                        help="Path for the benchmark CSV output")
    parser.add_argument("--warmups", type=int, default=5,
                        help="Warmup calls per report (discarded)")
    parser.add_argument("--measured", type=int, default=10,
                        help="Measured calls per report")
    parser.add_argument("--import-wait", type=int, default=120,
                        help="Max seconds to wait for Optimize import after seeding")

    args = parser.parse_args()
    run(args)


if __name__ == "__main__":
    main()
