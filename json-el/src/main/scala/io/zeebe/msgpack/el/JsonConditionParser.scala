/**
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
  * Copyright © 2017 camunda services GmbH (info@camunda.com)
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
package io.zeebe.msgpack.el

import io.zeebe.util.buffer.BufferUtil.wrapString

import scala.util.parsing.combinator.JavaTokenParsers

/**
  * Grammar: (based on JavaScript)
  *
  * {{{
  * condition   = disjunction | conjunction | comparison | '(' , condition , ')'
  *
  * disjunction = condition , '||', condition
  *
  * conjunction = condition , '&&', condition
  *
  * comparison  = literal , '==' | '!=' | '<' | '<=' | '>' | '>=' , literal
  *
  * literal     = json path | string | number | 'true' | 'false' | 'null'
  * }}}
  *
  */
object JsonConditionParser extends JavaTokenParsers {

  def parse(in: String): ParseResult[JsonCondition] = parseAll(condition, in)

  private lazy val condition: Parser[JsonCondition] = disjunction | failure("expected comparison, disjunction or conjunction.")

  private lazy val disjunction: Parser[JsonCondition] = chainl1(conjunction, "||" ^^^ Disjunction)

  private lazy val conjunction: Parser[JsonCondition] = chainl1(comparison | failure("expected comparison"), "&&" ^^^ Conjunction)

  private lazy val comparison: Parser[JsonCondition] = (
    literal ~ ("==" | "!=") ~! literal ^^ {
      case x ~ "==" ~ y => Equal(x, y)
      case x ~ "!=" ~ y => NotEqual(x, y)
    }
      | (number | jsonPath) ~ ("<=" | ">=" | "<" | ">") ~! numberOrJsonPath ^^ {
      case x ~ "<" ~ y => LessThan(x, y)
      case x ~ "<=" ~ y => LessOrEqual(x, y)
      case x ~ ">" ~ y => GreaterThan(x, y)
      case x ~ ">=" ~ y => GreaterOrEqual(x, y)
    }
      | "(" ~! condition ~ ")" ^^ { case _ ~ condition ~ _ => condition }
    ) withFailureMessage ("expected comparison operator ('==', '!=', '<', '<=', '>', '>=')")

  private lazy val literal: Parser[JsonObject] = (
    jsonPath
      | string
      | number
      | "true" ^^^ JsonBoolean(true)
      | "false" ^^^ JsonBoolean(false)
      | "null" ^^^ JsonNull
    ) withFailureMessage ("expected literal (JSON path, string, number, boolean, null)")

  private lazy val numberOrJsonPath: Parser[JsonObject] = (number | jsonPath) withFailureMessage ("expected number or JSON path")

  /**
    * Any JSON path expression which starts with '$' followed by non-space characters.
    */
  private lazy val jsonPath: Parser[JsonPath] =
    """\$([^\s])*""".r ^^ JsonPath

  /**
    * Double or single quotes enclosing a sequence of chars.
    *
    * @see JavaTokenParser#stringLiteral
    */
  private lazy val string: Parser[JsonString] = (
    stringLiteral ^^ (s => JsonString(wrapString(s.substring(1, s.length - 1))))
      | "'" ~> """([^'"\x00-\x1F\x7F\\]|\\[\\'"bfnrt]|\\u[a-fA-F0-9]{4})*""".r <~ "'" ^^ (s => JsonString(wrapString(s))))

  /**
    * An integer or floating point number.
    *
    * @see JavaTokenParser#floatingPointNumber
    * @see JavaTokenParser#wholeNumber
    */
  private lazy val number: Parser[JsonObject] = (
    """-?(\d+\.\d*|\d*\.\d+)([eE][+-]?\d+)?[fFdD]?""".r ^^ (n => JsonFloatingPointNumber(n.toDouble))
      |
      """-?\d+""".r ^^ (n => JsonWholeNumber(n.toLong)))

}
