"""
Zeebe Data Generator — continuous loop runner
=============================================
Repeatedly invokes ZeebeDataGeneratorCli in a loop until Ctrl+C.

Each iteration runs the Java generator as a subprocess, streaming its output
live. The generator automatically picks up from the last position/instanceKey
in Elasticsearch, so each iteration adds a new batch of data on top of the
previous one.

Usage:
    python3 gen_loop.py [options] [-- <generator args>]

Options (script-level):
    --project-dir  Path to the camunda monorepo root  (default: auto-detected)
    --mvnw         Path to mvnw wrapper                (default: <project-dir>/mvnw)
    --pause        Seconds to pause between runs       (default: 0)
    --build        Rebuild the module before starting  (default: false)

Everything after `--` is passed verbatim to ZeebeDataGeneratorCli, e.g.:

    python3 gen_loop.py -- --instances 100000 --update-rate 0.25 --prefix zeebe-record

If no generator args are given, ZeebeDataGeneratorCli uses its own defaults
(300 000 instances, prefix zeebe-record, etc.).
"""

import argparse
import os
import shlex
import signal
import subprocess
import sys
import time
from pathlib import Path

MAIN_CLASS = "io.camunda.optimize.test.generator.ZeebeDataGeneratorCli"
MODULE_POM  = "optimize/backend/pom.xml"
CP_FILE     = "/tmp/gen_loop_classpath.txt"


def detect_project_dir() -> Path:
    """Walk up from this script's location to find the monorepo root (contains mvnw)."""
    candidate = Path(__file__).resolve()
    for parent in [candidate, *candidate.parents]:
        if (parent / "mvnw").exists():
            return parent
    raise RuntimeError(
        "Could not auto-detect monorepo root. "
        "Pass --project-dir explicitly."
    )


def build_module(mvnw: Path, project_dir: Path) -> None:
    print("==> Building optimize/backend (this may take a minute)…")
    cmd = [str(mvnw), "install", "-f", MODULE_POM, "-Dquickly", "-T1C"]
    result = subprocess.run(cmd, cwd=project_dir)
    if result.returncode != 0:
        print("ERROR: build failed — aborting.", file=sys.stderr)
        sys.exit(1)
    print("==> Build done.\n")


def resolve_classpath(mvnw: Path, project_dir: Path) -> str:
    """
    Returns the full test-scope classpath for optimize/backend.
    Uses mvn dependency:build-classpath to collect all jars, then prepends
    target/test-classes and target/classes.
    Result is cached in CP_FILE so subsequent runs skip the Maven call.
    """
    cp_path = Path(CP_FILE)
    if cp_path.exists():
        cp = cp_path.read_text().strip()
        print(f"==> Using cached classpath from {CP_FILE}")
    else:
        print("==> Resolving classpath via Maven (one-time)…")
        cmd = [
            str(mvnw), "-f", MODULE_POM,
            "dependency:build-classpath",
            "-DincludeScope=test",
            f"-Dmdep.outputFile={CP_FILE}",
            "-q",
        ]
        result = subprocess.run(cmd, cwd=project_dir)
        if result.returncode != 0:
            print("ERROR: dependency:build-classpath failed.", file=sys.stderr)
            sys.exit(1)
        cp = cp_path.read_text().strip()
        print("==> Classpath resolved.\n")

    backend_target = project_dir / "optimize" / "backend" / "target"
    prefixes = [
        str(backend_target / "test-classes"),
        str(backend_target / "classes"),
    ]
    return os.pathsep.join(prefixes + [cp])


def run_generator(java: str, classpath: str, gen_args: list[str]) -> int:
    """Run ZeebeDataGeneratorCli once, streaming output. Returns exit code."""
    cmd = [java, "-cp", classpath, MAIN_CLASS] + gen_args
    # Print the command on the first run only (too long to repeat every time)
    process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    try:
        for line in process.stdout:
            print(line, end="", flush=True)
    except KeyboardInterrupt:
        process.send_signal(signal.SIGINT)
    process.wait()
    return process.returncode


def main() -> None:
    # Split args on `--`
    if "--" in sys.argv:
        split = sys.argv.index("--")
        script_argv = sys.argv[1:split]
        gen_argv    = sys.argv[split + 1:]
    else:
        script_argv = sys.argv[1:]
        gen_argv    = []

    parser = argparse.ArgumentParser(
        description="Run ZeebeDataGeneratorCli in a loop until Ctrl+C"
    )
    parser.add_argument("--project-dir", type=Path, default=None,
                        help="Path to camunda monorepo root (auto-detected by default)")
    parser.add_argument("--mvnw", type=Path, default=None,
                        help="Path to mvnw wrapper")
    parser.add_argument("--pause", type=float, default=0,
                        help="Seconds to pause between runs (default: 0)")
    parser.add_argument("--build", action="store_true",
                        help="Rebuild optimize/backend before starting")
    args = parser.parse_args(script_argv)

    project_dir: Path = args.project_dir or detect_project_dir()
    mvnw: Path        = args.mvnw or (project_dir / "mvnw")

    if not mvnw.exists():
        print(f"ERROR: mvnw not found at {mvnw}", file=sys.stderr)
        sys.exit(1)

    java = "java"  # assumes java is on PATH

    print(f"Project dir : {project_dir}")
    print(f"mvnw        : {mvnw}")
    print(f"Generator   : {MAIN_CLASS}")
    print(f"Generator args: {shlex.join(gen_argv) if gen_argv else '(defaults)'}")
    print(f"Pause between runs: {args.pause}s")
    print()

    if args.build:
        build_module(mvnw, project_dir)

    classpath = resolve_classpath(mvnw, project_dir)

    run_num = 0
    overall_start = time.monotonic()

    print("Press Ctrl+C to stop the loop.\n")

    try:
        while True:
            run_num += 1
            t0 = time.monotonic()
            print(f"{'━' * 72}")
            print(f"  Run #{run_num}  (total elapsed: {time.monotonic() - overall_start:.0f}s)")
            print(f"{'━' * 72}")

            rc = run_generator(java, classpath, gen_argv)
            elapsed = time.monotonic() - t0

            print(f"\n  Run #{run_num} finished in {elapsed:.1f}s  (exit code {rc})")

            if args.pause > 0:
                print(f"  Pausing {args.pause}s before next run…")
                time.sleep(args.pause)

            print()

    except KeyboardInterrupt:
        pass

    total = time.monotonic() - overall_start
    print(f"\n{'═' * 72}")
    print(f"  Stopped after {run_num} run(s)  ·  total time: {total:.1f}s")
    print(f"{'═' * 72}")


if __name__ == "__main__":
    main()
