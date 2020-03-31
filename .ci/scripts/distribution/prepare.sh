#!/bin/sh -eux

apt-get -qq update
apt-get install --no-install-recommends -qq -y jq libatomic1

useradd -u 1000 -m jenkins

chmod -R a+x .
