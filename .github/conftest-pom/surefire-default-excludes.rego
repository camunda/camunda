package main

import future.keywords.in

# This file contains rules checking Maven pom.xml files.

# Surefire and Failsafe only apply their default exclude **/*$* when <excludes> is empty.
# Without it, nested test classes are also scheduled on their own, run twice in parallel forks
# and corrupt the shared report file (INC-7122, INC-9015).
deny[msg] {
    [_, plugin] := walk(input)
    plugin.artifactId in {"maven-surefire-plugin", "maven-failsafe-plugin"}
    [_, cfg] := walk(plugin)
    excludes := array.flatten([cfg.excludes.exclude])
    count([e | some e in excludes; e != ""]) > 0
    not keeps_default_exclude(excludes)

    msg := sprintf("%s <excludes> drops the default **/*$* exclude, add <exclude>**/*$*</exclude> to it", [plugin.artifactId])
}

keeps_default_exclude(excludes) {
    some e in excludes
    contains(e, "$*")
}
