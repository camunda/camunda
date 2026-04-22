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
