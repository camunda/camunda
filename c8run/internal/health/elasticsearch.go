/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package health

import (
	"fmt"
	"os"
	"time"
)

func QueryElasticsearch(name string, url string) {
	if isRunning(name, url, 12, 10*time.Second) {
		fmt.Println(name + " has successfully been started.")
	} else {
		fmt.Println("Error: " + name + " did not start!")
		os.Exit(1)
	}
}
