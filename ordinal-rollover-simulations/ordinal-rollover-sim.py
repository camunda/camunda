#!/usr/bin/env python3

import argparse
import random


class Index:
    def __init__(self, name):
        self.name = name
        self.process_instances = {}
        self.ilm_deadline = None
        self.ordinal = None
        self.latest_process_instance_end_date = None

    @property
    def is_main(self):
      return self.name == "main"

    @property
    def is_dated(self):
      return "dated" in self.name

    @property
    def is_ordinal(self):
      return "ordinal" in self.name

    def add(self, process_instance):
        self.process_instances[process_instance.id] = process_instance
        if self.latest_process_instance_end_date is None or process_instance.end_date > self.latest_process_instance_end_date:
            self.latest_process_instance_end_date = process_instance.end_date


class ProcessInstance:
    def __init__(self, id, end_date):
        self.id = id
        self.end_date = end_date


class Simulation:
    def __init__(self, ordinal_rollover, retention_period=30, compaction_callback=None):
        self.ordinal_rollover = ordinal_rollover
        self.next_id = 1
        self.current_time = 0
        self.current_ordinal = 0
        self.retention_period = retention_period
        self.compaction_callback = compaction_callback
        self.indexes = {}
        self._main_index = self._ensure_index_exists("main")
        self.indexing_operations = 0
        self.delete_operations = 0
        self.indexes_dropped_by_ilm = 0
        self.docs_dropped_by_ilm = 0
        self.operations_cost_estimate = 0

    def new_ordinal_process_instance(self, duration):
      index = self.add_new_process_instance(f"ordinal-{self.current_ordinal:05}", duration)
      index.ordinal = self.current_ordinal

    def new_main_process_instance(self, duration):
      self.add_new_process_instance("main", duration)

    def add_new_process_instance(self, index_name, duration):
        index = self._ensure_index_exists(index_name)

        process_instance = ProcessInstance(self.next_id, self.current_time + duration)
        index.add(process_instance)
        self.next_id += 1
        self.indexing_operations += 1
        self.operations_cost_estimate += len(index.process_instances)

        return index

    def tick(self):
        self.current_time += 1
        self._archive_process_instances()
        self._check_for_finished_ordinals()
        self._apply_ilm_policies()
        ordinal_indexes = self.get_ordinal_indexes()
        next_ordinal = self.ordinal_rollover.next_ordinal(ordinal_indexes, self.current_ordinal, compaction_callback=self.compaction_callback)
        if next_ordinal != self.current_ordinal:
          verbose(f"Rolling over to ordinal index: {next_ordinal} at time {self.current_time}")
        self.current_ordinal = next_ordinal

    def get_ordinal_indexes(self):
      return [index for index in self.indexes.values() if index.is_ordinal]

    def compact_ordinal_index(self, compaction_style):
      verbose(f"Running compaction at time {self.current_time}")
      ordinal_indexes = self.get_ordinal_indexes()
      ordinal_indexes.sort(key=lambda index: index.ordinal, reverse=False)
      source_index = compaction_style.get_source_index(ordinal_indexes)
      target_index = compaction_style.get_target_index(ordinal_indexes)
      dropped_docs = 0
      for process_instance in source_index.process_instances.values():
        if (process_instance.end_date + self.retention_period) <= self.current_time:
          dropped_docs += 1
          continue
        target_index.add(process_instance)
        self.indexing_operations += 1
        self.operations_cost_estimate += len(target_index.process_instances)
      self.indexes_dropped_by_ilm += 1
      self.docs_dropped_by_ilm += dropped_docs
      del self.indexes[source_index.name]
      ordinal_indexes = self.get_ordinal_indexes()
      verbose(len(ordinal_indexes), "ordinal indexes remain after compaction")
      return ordinal_indexes

    def run_deleter(self, only_if_no_ilm_deadline=False):
      for index in self.indexes.values():
        if index.is_ordinal:
          if only_if_no_ilm_deadline and index.ilm_deadline is not None:
            continue
          for process_instance in list(index.process_instances.values()):
            if (process_instance.end_date + self.retention_period) <= self.current_time:
              del index.process_instances[process_instance.id]
              self.delete_operations += 1
              self.operations_cost_estimate += len(index.process_instances)

    def get_num_process_instances(self):
      num_processes = 0
      for index in self.indexes.values():
        num_processes += len(index.process_instances)
      return num_processes

    def get_biggest_index_size(self):
      biggest_size = 0
      for index in self.indexes.values():
        biggest_size = max(biggest_size, len(index.process_instances))
      return biggest_size

    def get_count_indexes_with_ilm_deadline(self):
      count = 0
      for index in self.indexes.values():
        if index.ilm_deadline is not None:
          count += 1
      return count

    def _ensure_index_exists(self, index_name):
      index = self.indexes.get(index_name)
      if index is None:
        index = Index(index_name)
        self.indexes[index_name] = index
      return index

    def _archive_process_instances(self):
      for process_instance in list(self._main_index.process_instances.values()):
        if process_instance.end_date <= self.current_time:
          dated_index = self._ensure_index_exists(f"dated-{process_instance.end_date:05}")
          if dated_index.ilm_deadline is None:
            dated_index.ilm_deadline = self.current_time + self.retention_period
          dated_index.process_instances[process_instance.id] = process_instance
          self.indexing_operations += 1
          self.operations_cost_estimate += len(dated_index.process_instances)
          del self._main_index.process_instances[process_instance.id]
          self.delete_operations += 1
          self.operations_cost_estimate += len(self._main_index.process_instances)

    def _check_for_finished_ordinals(self):
      for index in self.indexes.values():
        if index.ilm_deadline is None and index.is_ordinal:
          if self._check_if_index_finished(index):
            index.ilm_deadline = self.current_time + self.retention_period

    def _check_if_index_finished(self, index):
      if index.latest_process_instance_end_date is None:
        return False
      return index.latest_process_instance_end_date <= self.current_time

    def _apply_ilm_policies(self):
      for index in list(self.indexes.values()):
        if index.ilm_deadline is not None and index.ilm_deadline <= self.current_time:
          self.indexes_dropped_by_ilm += 1
          self.docs_dropped_by_ilm += len(index.process_instances)
          del self.indexes[index.name]


class CompactionStyle:
  def __init__(self, source, target):
    self.source = source
    self.target = target

  def _get_index(self, ordinal_indexes, age):
    if age == "oldest":
      return ordinal_indexes[0]
    if age == "oldest+1":
      return ordinal_indexes[1]
    elif age == "newest-1":
      return ordinal_indexes[-2]
    elif age == "newest":
      return ordinal_indexes[-1]
    raise ValueError(f"Unknown age: {age}")

  def get_source_index(self, ordinal_indexes):
    return self._get_index(ordinal_indexes, self.source)

  def get_target_index(self, ordinal_indexes):
    return self._get_index(ordinal_indexes, self.target)


class OrdinalRollover:
    def __init__(self, rollover_interval=1, rollover_size=1000, max_ordinals=30, circular_ordinals=False, circular_reverse=False):
        self.rollover_interval = rollover_interval
        self.rollover_size = rollover_size
        self.max_ordinals = max_ordinals
        self.circular_ordinals = circular_ordinals
        self.circular_reverse = circular_reverse
        self.time_since_last_rollover = 0

    def next_ordinal(self, ordinal_indexes, current_ordinal, compaction_callback=None):
        self.time_since_last_rollover += 1
        if self.time_since_last_rollover >= self.rollover_interval:
            return self._rollover(ordinal_indexes, current_ordinal, compaction_callback)
        current_index = None
        for index in ordinal_indexes:
          if index.ordinal == current_ordinal:
            current_index = index
            break
        if current_index is not None:
          if self.rollover_size and len(current_index.process_instances) >= self.rollover_size:
              return self._rollover(ordinal_indexes, current_ordinal, compaction_callback)
        return current_ordinal

    def _rollover(self, ordinal_indexes, current_ordinal, compaction_callback):
        self.time_since_last_rollover = 0
        if self.max_ordinals is not None and len(ordinal_indexes) >= self.max_ordinals:
          if compaction_callback is not None:
            ordinal_indexes = compaction_callback()
          else:
            if self.circular_ordinals:
              open_ordinals = [index for index in ordinal_indexes if index.ilm_deadline is None]
              if len(open_ordinals) > 1:
                open_ordinals.sort(key=lambda index: index.ordinal, reverse=self.circular_reverse)
                current_ordinal_index = -1
                for i, index in enumerate(open_ordinals):
                  if index.ordinal == current_ordinal:
                    current_ordinal_index = i
                    break
                if current_ordinal_index != -1:
                  next_ordinal_index = (current_ordinal_index + 1) % len(open_ordinals)
                  self.time_since_last_rollover = 0
                  return open_ordinals[next_ordinal_index].ordinal
            return current_ordinal
        highest_ordinal = current_ordinal
        for index in ordinal_indexes:
          if index.ordinal > highest_ordinal:
            highest_ordinal = index.ordinal
        return highest_ordinal + 1


def build_run_name(args):
  parts = [args.mode]
  if args.run_deleter:
    parts.append("deleter")
    if args.deleter_only_if_no_ilm:
      parts.append("only-if-no-ilm")
  if args.circular_ordinals:
    parts.append("circular")
    if args.circular_reverse:
      parts.append("reverse")
  if args.compaction:
    parts.append("compaction")
    compaction_style = args.compaction_source + " -> " + args.compaction_target
    if compaction_style != "oldest -> newest":
      parts.append(compaction_style)
  return "-".join(parts)


def main(args):
  compaction_callback = None
  if args.compaction:
    compaction_style = CompactionStyle(args.compaction_source, args.compaction_target)
    compaction_callback = lambda: sim.compact_ordinal_index(compaction_style)
  rollover = OrdinalRollover(
    rollover_interval=args.rollover_interval,
    rollover_size=args.rollover_size,
    max_ordinals=args.max_ordinals,
    circular_ordinals=args.circular_ordinals,
    circular_reverse=args.circular_reverse
  )
  sim = Simulation(rollover, compaction_callback=compaction_callback)
  if args.mode == "main":
    start_process_instance = sim.new_main_process_instance
  else:
    start_process_instance = sim.new_ordinal_process_instance

  run_sim = not args.csv_header_only

  # Simulate some process instances and time passing
  max_num_processes = 0
  biggest_index_size = 0
  if run_sim:
    for _ in range(args.ticks):
      for _ in range(args.rate):
        if args.duration_random == "uniform":
          duration = random.randint(args.duration_min, args.duration_max)
        else:
          duration = max(1, int(random.gauss((args.duration_min + args.duration_max) / 2, (args.duration_max - args.duration_min) / 6)))
        start_process_instance(duration=duration)
      sim.tick()
      if args.run_deleter:
        sim.run_deleter(only_if_no_ilm_deadline=args.deleter_only_if_no_ilm)
      max_num_processes = max(max_num_processes, sim.get_num_process_instances())
      biggest_index_size = max(biggest_index_size, sim.get_biggest_index_size())

    for index_name in sorted(sim.indexes.keys()):
      index = sim.indexes[index_name]
      verbose(f"Index: {index_name}, Process Instances: {len(index.process_instances)}")

  output_data = [
    ("Mode", args.mode),
    ("Run Name", build_run_name(args)),
    ("Retention Period", sim.retention_period),
    ("Duration Min", args.duration_min),
    ("Duration Max", args.duration_max),
    ("Rate", args.rate),
    ("Ticks", args.ticks),
    # specific to ordinals
    ("Rollover Interval", args.rollover_interval if args.mode == "ordinal" else "N/A"),
    ("Rollover Size", args.rollover_size if args.mode == "ordinal" else "N/A"),
    ("Max Ordinals", args.max_ordinals if args.mode == "ordinal" else "N/A"),
    ("Run Deleter", args.run_deleter if args.mode == "ordinal" else "N/A"),
    ("Deleter Only If No ILM Deadline", args.deleter_only_if_no_ilm if args.mode == "ordinal" else "N/A"),
    ("Circular Ordinals", args.circular_ordinals if args.mode == "ordinal" else "N/A"),
    ("Circular Reverse", args.circular_reverse if args.mode == "ordinal" else "N/A"),
    ("Compaction", args.compaction if args.mode == "ordinal" else "N/A"),
    ("Compaction Style", args.compaction_source + " -> " + args.compaction_target if args.mode == "ordinal" else "N/A"),
    ####
    ("Total indexes", len(sim.indexes)),
    ("Max process instances", max_num_processes),
    ("Indexes with ILM deadline", sim.get_count_indexes_with_ilm_deadline()),
    ("Indexes dropped by ILM", sim.indexes_dropped_by_ilm),
    ("Docs dropped by ILM", sim.docs_dropped_by_ilm),
    ("Biggest index size", biggest_index_size),
    ("Total indexing operations", sim.indexing_operations),
    ("Total delete operations", sim.delete_operations),
    ("Total operations", sim.indexing_operations + sim.delete_operations),
    ("Estimated total operations cost", sim.operations_cost_estimate)
  ]

  if args.csv_row or args.csv_header_only:
    import csv
    import sys
    writer = csv.writer(sys.stdout)
    if args.csv_header_only:
      writer.writerow([header for header, _ in output_data])
    else:
      writer.writerow([data for _, data in output_data])
  else:
    for header, data in output_data:
      print(f"{header}: {data}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--ticks", type=int, default=100, help="Number of ticks to simulate")
    parser.add_argument("--rate", type=int, default=5, help="Number of new process instances per tick")
    parser.add_argument("--duration-min", type=int, default=1, help="Minimum of how many ticks process instances last")
    parser.add_argument("--duration-max", type=int, default=10, help="Maximum of how many ticks process instances last")
    parser.add_argument("--duration-random", choices=["uniform", "gauss"], default="uniform", help="How to randomize process instance durations")
    parser.add_argument("--retention-period", type=int, default=30, help="How many ticks finished process instances are retained before deletion")
    parser.add_argument("--mode", choices=["main", "ordinal"], default="main", help="Whether to create process instances in the main index or in ordinal indexes")
    parser.add_argument("--rollover-interval", type=int, default=1, help="How many ticks between ordinal rollovers")
    parser.add_argument("--rollover-size", type=int, default=None, help="Rollover once the number of process instances in the current ordinal index reaches this size")
    parser.add_argument("--max-ordinals", type=int, default=None, help="Maximum number of ordinal indexes")
    parser.add_argument("--circular-ordinals", action="store_true", help="Whether to reuse ordinal indexes in a circular manner once the maximum number of ordinals is reached")
    parser.add_argument("--circular-reverse", action="store_true", help="Whether circular reuse proceeds in reverse order (starting with the highest ordinal index)")
    parser.add_argument("--compaction", action="store_true", help="Whether to compact ordinals indexes when we hit max ordinals")
    compaction_choices = ["oldest", "oldest+1", "newest-1", "newest"]
    parser.add_argument("--compaction-source", choices=compaction_choices, default="oldest")
    parser.add_argument("--compaction-target", choices=compaction_choices, default="newest")
    parser.add_argument("--run-deleter", action="store_true", help="Whether to run the deleter")
    parser.add_argument("--deleter-only-if-no-ilm", action="store_true", help="Whether to run the deleter on indexes that will be dropped by ILM")
    parser.add_argument("--verbose", action="store_true", help="Whether to print detailed output")
    parser.add_argument("--csv-row", action="store_true", help="Whether to output results as a single CSV row")
    parser.add_argument("--csv-header-only", action="store_true", help="Just output the CSV header, without any data rows")
    args = parser.parse_args()

    if args.verbose:
      verbose = print
    else:
      def verbose(*args, **kw):
        pass

    main(args)








