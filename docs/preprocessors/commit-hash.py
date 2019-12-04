#!/usr/bin/env python3

import json
import subprocess
import sys

CHAPTER_KEY = 'Chapter'
CONTENT_KEY = 'content'
NAME_KEY = 'name'
SUB_ITEMS_KEY = 'sub_items'
COMMIT_TEMPLATE = '{{commit}}'


def eprint(*args, **kwargs):
    print(*args, file=sys.stderr, **kwargs)


def read_book():
    _context, book = json.load(sys.stdin)
    return book


def output_book(book):
    json.dump(book, sys.stdout)


def process_item(item, commit_hash):
    if not CHAPTER_KEY in item:
        pass

    chapter = item[CHAPTER_KEY]
    chapter[CONTENT_KEY] = chapter[CONTENT_KEY].replace(
        '{{commit}}', commit_hash)
    for sub_section in chapter[SUB_ITEMS_KEY]:
        process_item(sub_section, commit_hash)


def replace_commit_hash(book):
    try:
        commit_hash = subprocess.run(
            ['git', 'rev-parse', 'HEAD'], check=True, stdout=subprocess.PIPE).stdout.decode('utf-8').strip()
    except:
        eprint('Skipping commit preprocessing as "git rev-parse HEAD" failed')
        # dump unprocessed book to continue pipeline
        output_book(book)
        sys.exit(0)

    eprint(
        'Replacing commit template {} with hash {}'.format(COMMIT_TEMPLATE, commit_hash))

    for section in book['sections']:
        process_item(section, commit_hash)


def main():
    if len(sys.argv) > 1:
        if sys.argv[1] == 'supports':
            # support all renderer, sys.argv[2] is the renderer name
            sys.exit(0)

    # read book from stdin
    book = read_book()

    replace_commit_hash(book)

    # write processed book to stdout
    output_book(book)


if __name__ == "__main__":
    main()
