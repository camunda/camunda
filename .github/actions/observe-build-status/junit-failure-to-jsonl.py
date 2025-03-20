
import sys
import xml.etree.ElementTree

def parse_junit_xml(file_path):
    tree = xml.etree.ElementTree.parse(file_path)
    root = tree.getroot()

    if root.tag == 'testsuite':
        parse_testsuite(root)
    elif root.tag == 'testsuites':
        for testsuite in root:
            parse_testsuite(testsuite)

def parse_testsuite(testsuite):
    for testcase in testsuite.findall('testcase'):
        test_class_name = testcase.get('classname')
        test_class_duration_milliseconds = int(float(testsuite.get('time'))*1000)
        test_name = testcase.get('name')
        test_duration_milliseconds = int(float(testcase.get('time'))*1000)

        if len(testcase.findall('skipped')) > 0:
            test_status = 'skipped'
        elif len(testcase.findall('failure')) > 0:
            test_status = 'failure'
        else:
            # test_status = 'success'
            # Do not submit test status of successes, since we could end up with
            # two entries for one test if that test also was flaky!
            continue

        print((
            f'{{"test_class_name": "{test_class_name}", '
            f'"test_class_duration_milliseconds": {test_class_duration_milliseconds}, '
            f'"test_name": "{test_name}", '
            f'"test_status": "{test_status}", '
            f'"test_duration_milliseconds": {test_duration_milliseconds}}}'
        ))

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 junit-to-jsonl.py path/to/TEST-sometestname.xml", file=sys.stderr)
        sys.exit(1)

    parse_junit_xml(sys.argv[1])
