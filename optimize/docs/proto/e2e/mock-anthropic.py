#!/usr/bin/env python3
"""Mock Anthropic Messages API for the local agentic end-to-end test.

The AI Agent connector talks to it through the template's "custom" Anthropic
backend, so agent runs go through the real engine and connector without an API
key or cost. Each request answers with the next tool the agent has not called
yet, then with a final text answer once every tool has a result. Token usage is
reported on every response so the agent's metrics are non-zero.

Usage: python3 -I mock-anthropic.py [port]   (default port 8099)
"""

import itertools
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8099
_ids = itertools.count(1)

SAMPLE_VALUES = {"string": "A-1001", "number": 3, "integer": 3, "boolean": True}


def sample_input(schema):
    props = (schema or {}).get("properties", {})
    return {
        name: SAMPLE_VALUES.get(spec.get("type"), "A-1001") for name, spec in props.items()
    }


def called_tool_names(messages):
    names = set()
    for message in messages:
        content = message.get("content")
        if message.get("role") == "assistant" and isinstance(content, list):
            names.update(b["name"] for b in content if b.get("type") == "tool_use")
    return names


def next_reply(body):
    tools = body.get("tools", [])
    done = called_tool_names(body.get("messages", []))
    pending = [t for t in tools if t["name"] not in done]
    if pending:
        tool = pending[0]
        block = {
            "type": "tool_use",
            "id": f"toolu_mock_{next(_ids)}",
            "name": tool["name"],
            "input": sample_input(tool.get("input_schema")),
        }
        return [block], "tool_use"
    text = "Your order A-1001 is eligible and refund R-A-1001 has been created."
    return [{"type": "text", "text": text}], "end_turn"


def usage():
    return {
        "input_tokens": 1200,
        "output_tokens": 150,
        "cache_creation_input_tokens": 300,
        "cache_read_input_tokens": 600,
    }


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.rfile.read(length) or b"{}")
        content, stop_reason = next_reply(body)
        model = body.get("model", "mock-model")
        summary = content[0].get("name") or "final answer"
        print(f"[mock] {self.path} tools={len(body.get('tools', []))} -> {summary}", flush=True)
        if body.get("stream"):
            self.stream(model, content, stop_reason)
        else:
            self.send_json(
                {
                    "id": f"msg_mock_{next(_ids)}",
                    "type": "message",
                    "role": "assistant",
                    "model": model,
                    "content": content,
                    "stop_reason": stop_reason,
                    "stop_sequence": None,
                    "usage": usage(),
                }
            )

    def send_json(self, payload):
        data = json.dumps(payload).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def stream(self, model, content, stop_reason):
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.end_headers()
        u = usage()

        def event(name, data):
            self.wfile.write(f"event: {name}\ndata: {json.dumps(data)}\n\n".encode())
            self.wfile.flush()

        start_usage = dict(u, output_tokens=1)
        event(
            "message_start",
            {
                "type": "message_start",
                "message": {
                    "id": f"msg_mock_{next(_ids)}",
                    "type": "message",
                    "role": "assistant",
                    "model": model,
                    "content": [],
                    "stop_reason": None,
                    "stop_sequence": None,
                    "usage": start_usage,
                },
            },
        )
        for index, block in enumerate(content):
            if block["type"] == "tool_use":
                event(
                    "content_block_start",
                    {
                        "type": "content_block_start",
                        "index": index,
                        "content_block": dict(block, input={}),
                    },
                )
                delta = {"type": "input_json_delta", "partial_json": json.dumps(block["input"])}
            else:
                event(
                    "content_block_start",
                    {
                        "type": "content_block_start",
                        "index": index,
                        "content_block": {"type": "text", "text": ""},
                    },
                )
                delta = {"type": "text_delta", "text": block["text"]}
            event("content_block_delta", {"type": "content_block_delta", "index": index, "delta": delta})
            event("content_block_stop", {"type": "content_block_stop", "index": index})
        event(
            "message_delta",
            {
                "type": "message_delta",
                "delta": {"stop_reason": stop_reason, "stop_sequence": None},
                "usage": {"output_tokens": u["output_tokens"]},
            },
        )
        event("message_stop", {"type": "message_stop"})

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    print(f"[mock] Anthropic mock listening on :{PORT}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
