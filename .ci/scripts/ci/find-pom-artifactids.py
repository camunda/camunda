#!/usr/bin/python3

import os
import xml.etree.ElementTree as ET

def find_pom_files(start_dir):
    pom_files = []
    for root, dirs, files in os.walk(start_dir):
        if 'pom.xml' in files:
            pom_files.append(os.path.join(root, 'pom.xml'))
    return pom_files

def get_artifact_id(pom_path):
    # Maven POM files use the "http://maven.apache.org/POM/4.0.0" namespace
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}

    return ET.parse(pom_path).getroot().find('m:artifactId', ns).text

if __name__ == "__main__":
    for pom_file in find_pom_files('.'):
        print(get_artifact_id(pom_file))
