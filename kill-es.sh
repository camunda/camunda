#!/bin/sh

if [ -z $(ps aux | grep -v grep | grep elasticsearch | awk '{ print $2 }') ]
then
    kill -9  $(ps aux | grep -v grep | grep elasticsearch | awk '{ print $2 }')
fi