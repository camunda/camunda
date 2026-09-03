import {
  Alternation,
  Alternative,
  NonTerminal,
  Option,
  Repetition,
  RepetitionMandatory,
  RepetitionMandatoryWithSeparator,
  RepetitionWithSeparator,
  Rule,
  Terminal
} from "./model"
import { IProduction } from "@chevrotain/types"

export abstract class GAstVisitor {
  public visit(node: IProduction): any {
    const nodeAny: any = node
    switch (nodeAny.constructor) {
      case NonTerminal:
        return this.visitNonTerminal(nodeAny)
      case Alternative:
        return this.visitAlternative(nodeAny)
      case Option:
        return this.visitOption(nodeAny)
      case RepetitionMandatory:
        return this.visitRepetitionMandatory(nodeAny)
      case RepetitionMandatoryWithSeparator:
        return this.visitRepetitionMandatoryWithSeparator(nodeAny)
      case RepetitionWithSeparator:
        return this.visitRepetitionWithSeparator(nodeAny)
      case Repetition:
        return this.visitRepetition(nodeAny)
      case Alternation:
        return this.visitAlternation(nodeAny)
      case Terminal:
        return this.visitTerminal(nodeAny)
      case Rule:
        return this.visitRule(nodeAny)
      /* istanbul ignore next */
      default:
        throw Error("non exhaustive match")
    }
  }

  /* istanbul ignore next - testing the fact a NOOP function exists is non-trivial  */
  public visitNonTerminal(node: NonTerminal): any {}

  /* istanbul ignore next - testing the fact a NOOP function exists is non-trivial  */
  public visitAlternative(node: Alternative): any {}

  /* istanbul ignore next - testing the fact a NOOP function exists is non-trivial  */
  public visitOption(node: Option): any {}

  /* istanbul ignore next - testing the fact a NOOP function exists is non-trivial  */
  public visitRepetition(node: Repetition): any {}

  /* istanbul ignore next - testing the fact a NOOP function exists is non-trivial  */
  public visitRepetitionMandatory(node: RepetitionMandatory): any {}

  /* istanbul ignore next - testing the fact a NOOP function exists is non-trivial  */
  public visitRepetitionMandatoryWithSeparator(
    node: RepetitionMandatoryWithSeparator
  ): any {}

  /* istanbul ignore next - testing the fact a NOOP function exists is non-trivial  */
  public visitRepetitionWithSeparator(node: RepetitionWithSeparator): any {}

  /* istanbul ignore next - testing the fact a NOOP function exists is non-trivial  */
  public visitAlternation(node: Alternation): any {}

  /* istanbul ignore next - testing the fact a NOOP function exists is non-trivial  */
  public visitTerminal(node: Terminal): any {}

  /* istanbul ignore next - testing the fact a NOOP function exists is non-trivial  */
  public visitRule(node: Rule): any {}
}
