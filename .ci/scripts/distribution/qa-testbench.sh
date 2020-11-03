#!/bin/sh -eux

chmod +x clients/go/cmd/zbctl/dist/zbctl

alias zbctl="clients/go/cmd/zbctl/dist/zbctl"

zbctl create instance qa-protocol --variables "${QA_RUN_VARIABLES}"
