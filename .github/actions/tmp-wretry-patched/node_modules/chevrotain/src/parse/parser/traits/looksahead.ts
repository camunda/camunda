import {
  buildAlternativesLookAheadFunc,
  buildLookaheadFuncForOptionalProd,
  buildLookaheadFuncForOr,
  buildSingleAlternativeLookaheadFunction,
  PROD_TYPE
} from "../../grammar/lookahead"
import forEach from "lodash/forEach"
import has from "lodash/has"
import {
  DEFAULT_PARSER_CONFIG,
  LookAheadSequence,
  TokenMatcher
} from "../parser"
import { IOrAlt, IParserConfig } from "@chevrotain/types"
import {
  AT_LEAST_ONE_IDX,
  AT_LEAST_ONE_SEP_IDX,
  getKeyForAutomaticLookahead,
  MANY_IDX,
  MANY_SEP_IDX,
  OPTION_IDX,
  OR_IDX
} from "../../grammar/keys"
import { MixedInParser } from "./parser_traits"
import {
  Alternation,
  GAstVisitor,
  Option,
  Repetition,
  RepetitionMandatory,
  RepetitionMandatoryWithSeparator,
  RepetitionWithSeparator,
  Rule
} from "@chevrotain/gast"
import { getProductionDslName } from "@chevrotain/gast"

/**
 * Trait responsible for the lookahead related utilities and optimizations.
 */
export class LooksAhead {
  maxLookahead: number
  lookAheadFuncsCache: any
  dynamicTokensEnabled: boolean

  initLooksAhead(config: IParserConfig) {
    this.dynamicTokensEnabled = has(config, "dynamicTokensEnabled")
      ? (config.dynamicTokensEnabled as boolean) // assumes end user provides the correct config value/type
      : DEFAULT_PARSER_CONFIG.dynamicTokensEnabled

    this.maxLookahead = has(config, "maxLookahead")
      ? (config.maxLookahead as number) // assumes end user provides the correct config value/type
      : DEFAULT_PARSER_CONFIG.maxLookahead

    this.lookAheadFuncsCache = new Map()
  }

  preComputeLookaheadFunctions(this: MixedInParser, rules: Rule[]): void {
    forEach(rules, (currRule) => {
      this.TRACE_INIT(`${currRule.name} Rule Lookahead`, () => {
        const {
          alternation,
          repetition,
          option,
          repetitionMandatory,
          repetitionMandatoryWithSeparator,
          repetitionWithSeparator
        } = collectMethods(currRule)

        forEach(alternation, (currProd) => {
          const prodIdx = currProd.idx === 0 ? "" : currProd.idx
          this.TRACE_INIT(`${getProductionDslName(currProd)}${prodIdx}`, () => {
            const laFunc = buildLookaheadFuncForOr(
              currProd.idx,
              currRule,
              currProd.maxLookahead || this.maxLookahead,
              currProd.hasPredicates,
              this.dynamicTokensEnabled,
              this.lookAheadBuilderForAlternatives
            )

            const key = getKeyForAutomaticLookahead(
              this.fullRuleNameToShort[currRule.name],
              OR_IDX,
              currProd.idx
            )
            this.setLaFuncCache(key, laFunc)
          })
        })

        forEach(repetition, (currProd) => {
          this.computeLookaheadFunc(
            currRule,
            currProd.idx,
            MANY_IDX,
            PROD_TYPE.REPETITION,
            currProd.maxLookahead,
            getProductionDslName(currProd)
          )
        })

        forEach(option, (currProd) => {
          this.computeLookaheadFunc(
            currRule,
            currProd.idx,
            OPTION_IDX,
            PROD_TYPE.OPTION,
            currProd.maxLookahead,
            getProductionDslName(currProd)
          )
        })

        forEach(repetitionMandatory, (currProd) => {
          this.computeLookaheadFunc(
            currRule,
            currProd.idx,
            AT_LEAST_ONE_IDX,
            PROD_TYPE.REPETITION_MANDATORY,
            currProd.maxLookahead,
            getProductionDslName(currProd)
          )
        })

        forEach(repetitionMandatoryWithSeparator, (currProd) => {
          this.computeLookaheadFunc(
            currRule,
            currProd.idx,
            AT_LEAST_ONE_SEP_IDX,
            PROD_TYPE.REPETITION_MANDATORY_WITH_SEPARATOR,
            currProd.maxLookahead,
            getProductionDslName(currProd)
          )
        })

        forEach(repetitionWithSeparator, (currProd) => {
          this.computeLookaheadFunc(
            currRule,
            currProd.idx,
            MANY_SEP_IDX,
            PROD_TYPE.REPETITION_WITH_SEPARATOR,
            currProd.maxLookahead,
            getProductionDslName(currProd)
          )
        })
      })
    })
  }

  computeLookaheadFunc(
    this: MixedInParser,
    rule: Rule,
    prodOccurrence: number,
    prodKey: number,
    prodType: PROD_TYPE,
    prodMaxLookahead: number | undefined,
    dslMethodName: string
  ): void {
    this.TRACE_INIT(
      `${dslMethodName}${prodOccurrence === 0 ? "" : prodOccurrence}`,
      () => {
        const laFunc = buildLookaheadFuncForOptionalProd(
          prodOccurrence,
          rule,
          prodMaxLookahead || this.maxLookahead,
          this.dynamicTokensEnabled,
          prodType,
          this.lookAheadBuilderForOptional
        )
        const key = getKeyForAutomaticLookahead(
          this.fullRuleNameToShort[rule.name],
          prodKey,
          prodOccurrence
        )
        this.setLaFuncCache(key, laFunc)
      }
    )
  }

  lookAheadBuilderForOptional(
    this: MixedInParser,
    alt: LookAheadSequence,
    tokenMatcher: TokenMatcher,
    dynamicTokensEnabled: boolean
  ): () => boolean {
    return buildSingleAlternativeLookaheadFunction(
      alt,
      tokenMatcher,
      dynamicTokensEnabled
    )
  }

  lookAheadBuilderForAlternatives(
    this: MixedInParser,
    alts: LookAheadSequence[],
    hasPredicates: boolean,
    tokenMatcher: TokenMatcher,
    dynamicTokensEnabled: boolean
  ): (orAlts: IOrAlt<any>[]) => number | undefined {
    return buildAlternativesLookAheadFunc(
      alts,
      hasPredicates,
      tokenMatcher,
      dynamicTokensEnabled
    )
  }

  // this actually returns a number, but it is always used as a string (object prop key)
  getKeyForAutomaticLookahead(
    this: MixedInParser,
    dslMethodIdx: number,
    occurrence: number
  ): number {
    const currRuleShortName: any = this.getLastExplicitRuleShortName()
    return getKeyForAutomaticLookahead(
      currRuleShortName,
      dslMethodIdx,
      occurrence
    )
  }

  getLaFuncFromCache(this: MixedInParser, key: number): Function {
    return this.lookAheadFuncsCache.get(key)
  }

  /* istanbul ignore next */
  setLaFuncCache(this: MixedInParser, key: number, value: Function): void {
    this.lookAheadFuncsCache.set(key, value)
  }
}

class DslMethodsCollectorVisitor extends GAstVisitor {
  public dslMethods: {
    option: Option[]
    alternation: Alternation[]
    repetition: Repetition[]
    repetitionWithSeparator: RepetitionWithSeparator[]
    repetitionMandatory: RepetitionMandatory[]
    repetitionMandatoryWithSeparator: RepetitionMandatoryWithSeparator[]
  } = {
    option: [],
    alternation: [],
    repetition: [],
    repetitionWithSeparator: [],
    repetitionMandatory: [],
    repetitionMandatoryWithSeparator: []
  }

  reset() {
    this.dslMethods = {
      option: [],
      alternation: [],
      repetition: [],
      repetitionWithSeparator: [],
      repetitionMandatory: [],
      repetitionMandatoryWithSeparator: []
    }
  }

  public visitOption(option: Option): void {
    this.dslMethods.option.push(option)
  }

  public visitRepetitionWithSeparator(manySep: RepetitionWithSeparator): void {
    this.dslMethods.repetitionWithSeparator.push(manySep)
  }

  public visitRepetitionMandatory(atLeastOne: RepetitionMandatory): void {
    this.dslMethods.repetitionMandatory.push(atLeastOne)
  }

  public visitRepetitionMandatoryWithSeparator(
    atLeastOneSep: RepetitionMandatoryWithSeparator
  ): void {
    this.dslMethods.repetitionMandatoryWithSeparator.push(atLeastOneSep)
  }

  public visitRepetition(many: Repetition): void {
    this.dslMethods.repetition.push(many)
  }

  public visitAlternation(or: Alternation): void {
    this.dslMethods.alternation.push(or)
  }
}

const collectorVisitor = new DslMethodsCollectorVisitor()
export function collectMethods(rule: Rule): {
  option: Option[]
  alternation: Alternation[]
  repetition: Repetition[]
  repetitionWithSeparator: RepetitionWithSeparator[]
  repetitionMandatory: RepetitionMandatory[]
  repetitionMandatoryWithSeparator: RepetitionMandatoryWithSeparator[]
} {
  collectorVisitor.reset()
  rule.accept(collectorVisitor)
  const dslMethods = collectorVisitor.dslMethods
  // avoid uncleaned references
  collectorVisitor.reset()
  return <any>dslMethods
}
