#!/usr/bin/env python3
"""Applies the AI Agent Sub-process element template to the ad-hoc sub-process of
a BPMN file, the way a modeler would, so it can be deployed without one.

  python3 -I apply-template.py mock in.bpmn out.bpmn   # agent calls mock-anthropic.py
  python3 -I apply-template.py real in.bpmn out.bpmn   # agent calls Anthropic with
                                                       # {{secrets.ANTHROPIC_API_KEY}}

Only template properties whose conditions hold for the chosen values are written,
with the template's defaults for everything not set here.
"""

import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).resolve().parent
TEMPLATE = json.loads((HERE.parent / "agenticai-ai-agent-subprocess.v2.json").read_text())

NS = {
    "bpmn": "http://www.omg.org/spec/BPMN/20100524/MODEL",
    "zeebe": "http://camunda.org/schema/zeebe/1.0",
    "bpmndi": "http://www.omg.org/spec/BPMN/20100524/DI",
    "dc": "http://www.omg.org/spec/DD/20100524/DC",
    "di": "http://www.omg.org/spec/DD/20100524/DI",
    "modeler": "http://camunda.org/schema/modeler/1.0",
}
for prefix, uri in NS.items():
    ET.register_namespace(prefix, uri)
Z = "{%s}" % NS["zeebe"]

SYSTEM_PROMPT = (
    "You are a customer support agent for an online shop. Use the tools to look up the "
    "order, check refund eligibility, and create a refund only when the order is eligible. "
    "Answer the customer briefly with the outcome."
)
COMMON = {
    "provider.type": "anthropic",
    "data.systemPrompt.prompt": "=" + json.dumps(SYSTEM_PROMPT),
    "data.userPrompt.prompt": "=ticket",
}
MODES = {
    "mock": {
        "provider.anthropic.backend.type": "custom",
        "provider.anthropic.backend.custom.endpoint": "http://host.docker.internal:8099",
        "provider.anthropic.backend.custom.authentication.type": "none",
        "provider.anthropic.model.model": "mock-claude",
    },
    "real": {
        "provider.anthropic.backend.type": "anthropic-api",
        "provider.anthropic.backend.anthropic.apiKey": "{{secrets.ANTHROPIC_API_KEY}}",
        "provider.anthropic.model.model": "claude-haiku-5-5",
    },
}
NUMERIC_INPUTS = {"data.memory.contextWindowSize", "data.limits.maxModelCalls"}


def binding_key(prop):
    b = prop.get("binding", {})
    return b.get("name") or b.get("property") or b.get("key") or b.get("source")


def main(mode, source, target):
    chosen = {**COMMON, **MODES[mode]}
    props = TEMPLATE["properties"]
    values = {(p.get("id") or binding_key(p)): chosen.get(binding_key(p), p.get("value")) for p in props}

    def holds(cond):
        if not cond:
            return True
        if "allMatch" in cond:
            return all(holds(c) for c in cond["allMatch"])
        value = values.get(cond["property"])
        if "equals" in cond:
            return value == cond["equals"]
        if "oneOf" in cond:
            return value in cond["oneOf"]
        return True

    tree = ET.parse(source)
    element = tree.getroot().find(".//bpmn:adHocSubProcess", NS)
    element.set(Z + "modelerTemplate", TEMPLATE["id"])
    element.set(Z + "modelerTemplateVersion", str(TEMPLATE["version"]))
    for doc in element.findall("bpmn:documentation", NS):
        element.remove(doc)
    ext = element.find("bpmn:extensionElements", NS)
    if ext is None:
        ext = ET.Element("{%s}extensionElements" % NS["bpmn"])
        element.insert(0, ext)

    def child(tag):
        node = ext.find(Z + tag)
        return node if node is not None else ET.SubElement(ext, Z + tag)

    inputs, outputs, headers, properties = [], [], [], []
    for prop in props:
        if not holds(prop.get("condition")):
            continue
        b = prop["binding"]
        kind, key = b["type"], binding_key(prop)
        value = chosen.get(key, prop.get("value"))
        if value is None or value == "":
            continue
        if kind == "zeebe:taskDefinition":
            child("taskDefinition").set(b["property"], str(value))
        elif kind == "zeebe:adHoc":
            child("adHoc").set(b["property"], str(value))
        elif kind == "zeebe:agentDefinition":
            child("agentDefinition").set(b["property"], str(value))
        elif kind == "zeebe:property":
            properties.append((b["name"], str(value)))
        elif kind == "zeebe:taskHeader":
            headers.append((b["key"], str(value)))
        elif kind == "zeebe:output":
            outputs.append((b["source"], str(value)))
        elif kind == "zeebe:input":
            if isinstance(value, bool):
                value = "=" + str(value).lower()
            elif key in NUMERIC_INPUTS or isinstance(value, (int, float)):
                value = "=" + str(value)
            inputs.append((b["name"], str(value)))

    io = child("ioMapping")
    for name, value in inputs:
        ET.SubElement(io, Z + "input", {"source": value, "target": name})
    for source_expr, name in outputs:
        ET.SubElement(io, Z + "output", {"source": source_expr, "target": name})
    task_headers = child("taskHeaders")
    for key, value in headers:
        ET.SubElement(task_headers, Z + "header", {"key": key, "value": value})
    zeebe_properties = child("properties")
    for name, value in properties:
        ET.SubElement(zeebe_properties, Z + "property", {"name": name, "value": value})
    tree.write(target, xml_declaration=True, encoding="UTF-8")
    print(f"wrote {target} ({mode}, {len(inputs)} inputs)")


if __name__ == "__main__":
    if len(sys.argv) != 4 or sys.argv[1] not in MODES:
        sys.exit(__doc__)
    main(*sys.argv[1:])
