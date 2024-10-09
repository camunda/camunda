package main

import (
	"fmt"
	"os"
	"syscall"
	"unsafe"
)

func process_tree(pid int) []*os.Process {
	// fmt.Println("The pid is ", pid)
	rootPid := uint32(pid)
	// fmt.Println("The unsigned pid is ", rootPid)

	tree, err := getTreePids(rootPid)
	if err != nil {
		fmt.Println(err)
	}
	// fmt.Println("tree [", len(tree), "]:\t", tree)

	processList := make([]*os.Process, len(tree))
	for i, pid := range tree {
		process, err := os.FindProcess(int(pid))
		if err != nil {
			fmt.Println("Process " + string(pid) + " not found")
			continue
		}
		processList[i] = process
	}
	return processList
}

// getTreePids will return a list of pids that represent the tree of process pids originating from the specified one.
// (they are ordered: [parent, 1 gen child, 2 gen child, ...])
func getTreePids(rootPid uint32) ([]uint32, error) {
	// https://docs.microsoft.com/en-us/windows/win32/api/tlhelp32/ns-tlhelp32-processentry32
	procEntry := syscall.ProcessEntry32{}
	parentLayer := []uint32{rootPid}
	treePids := parentLayer
	foundRootPid := false

	snapshot, err := syscall.CreateToolhelp32Snapshot(uint32(syscall.TH32CS_SNAPPROCESS), 0)
	if err != nil {
		return nil, err
	}
	defer syscall.CloseHandle(snapshot)

	procEntry.Size = uint32(unsafe.Sizeof(procEntry))

	for {
		// set procEntry to the first process in the snapshot
		err = syscall.Process32First(snapshot, &procEntry)
		if err != nil {
			return nil, err
		}

		// loop through the processes in the snapshot, if the parent pid of the analyzed process
		// is in in the parent layer, append the analyzed process pid in the child layer
		var childLayer []uint32
		for {
			if procEntry.ProcessID == rootPid {
				foundRootPid = true
			}

			if contains(parentLayer, procEntry.ParentProcessID) {
				// avoid adding a pid if it's already contained in treePids
				// useful for pid 0 whose ppid is 0 and would lead to recursion (windows)
				if !contains(treePids, procEntry.ProcessID) {
					childLayer = append(childLayer, procEntry.ProcessID)
				}
			}

			// advance to next process in snapshot
			err = syscall.Process32Next(snapshot, &procEntry)
			if err != nil {
				// if there aren't anymore processes to be analyzed, break out of the loop
				break
			}
		}

		// if the specified rootPid is not found, return error
		if !foundRootPid {
			return nil, fmt.Errorf("specified rootPid not found")
		}

		// fmt.Println(childLayer)

		// there are no more child processes, return the process tree
		if len(childLayer) == 0 {
			return treePids, nil
		}

		// append the child layer to the tree pids
		treePids = append(treePids, childLayer...)

		// to analyze the next layer, set the child layer to be the new parent layer
		parentLayer = childLayer
	}
}

func contains(list []uint32, e uint32) bool {
	for _, l := range list {
		if l == e {
			return true
		}
	}
	return false
}
