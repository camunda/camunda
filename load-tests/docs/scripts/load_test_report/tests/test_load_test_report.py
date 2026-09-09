import load_test_report


def test_should_build_parser():
    parser = load_test_report.build_parser()

    assert parser.prog == "load_test_report.py"


def test_should_run_without_arguments():
    assert load_test_report.run([]) == 0
