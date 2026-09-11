import load_test_report


def test_should_build_parser():
    parser = load_test_report.build_parser()

    assert parser.prog == "load-test-report"


def test_should_run_without_arguments():
    assert load_test_report.run([]) == 0


def test_should_return_argparse_exit_code_for_help():
    assert load_test_report.run(["--help"]) == 0
