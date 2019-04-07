#!/bin/sh -eux

if ${LIVE}; then
    FOLDER=docs.zeebe.io
else
    FOLDER=stage.docs.zeebe.io
fi

rsync -azv --delete-after "docs/book/" jenkins_docs_zeebe_io@vm29.camunda.com:"/var/www/camunda/${FOLDER}/" -e "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no"

