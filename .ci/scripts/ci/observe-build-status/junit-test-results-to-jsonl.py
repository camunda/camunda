import sys
import xml.etree.ElementTree
import json


def extract_data(testcase, testsuite):
    test_class_name = testcase.get('classname')
    test_class_duration_milliseconds = int(float(testsuite.get('time', 0)) * 1000)
    test_name = testcase.get('name').removesuffix(' (Flaky Test)')
    test_duration_milliseconds = int(float(testcase.get('time', 0)) * 1000)

    return {
        "test_class_name": test_class_name,
        "test_class_duration_milliseconds": test_class_duration_milliseconds,
        "test_name": test_name,
        "test_duration_milliseconds": test_duration_milliseconds
    }

def determine_test_status(testcase):
    if testcase.findall('skipped'):
        return 'skipped'
    elif testcase.findall('failure'):
        return 'failure'
    elif testcase.findall('error'):
        return 'error'
    else:
        return 'success'

def parse_test_files(file_paths):
    for file_path in file_paths:
        is_flaky = file_path.endswith('FLAKY.xml')
        
        try:
            tree = xml.etree.ElementTree.parse(file_path)
            root = tree.getroot()
            testsuites = [root] if root.tag == 'testsuite' else root.findall('testsuite')
            
            for testsuite in testsuites:
                for testcase in testsuite.findall('testcase'):
                    test_data = extract_data(testcase, testsuite)
                    test_key = f"{test_data['test_class_name']}::{test_data['test_name']}"
                    yield test_data, test_key, is_flaky, testcase
                    
        except Exception as e:
            print(f"Error processing {file_path}: {e}", file=sys.stderr)

def process_all_files(file_paths):
    flaky_tests = set()
    all_results = []
    
    for test_data, test_key, is_flaky, testcase in parse_test_files(file_paths):
        if is_flaky:
            print("DEBUG: Found flaky test", file=sys.stderr)
            print(f"Marking test as flaky: key: {test_key}, testcase: {testcase}, test_data: {test_data}", file=sys.stderr)
            flaky_tests.add(test_key)
            test_data['test_status'] = 'flaky'
            all_results.append(test_data)
    
    for test_data, test_key, is_flaky, testcase in parse_test_files(file_paths):
        if not is_flaky and test_key not in flaky_tests:
            test_data['test_status'] = determine_test_status(testcase)
            all_results.append(test_data)
    
    return all_results

def output_results(results):
    for result in results:
        json_line = json.dumps(result)
        print(json_line)

if __name__ == "__main__":
    file_paths = [line.strip() for line in sys.stdin if line.strip()]
    results = process_all_files(file_paths)
    output_results(results)