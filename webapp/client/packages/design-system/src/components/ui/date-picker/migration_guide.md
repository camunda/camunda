# DatePicker — Carbon → shadcn migration guide

## No 1-to-1 primitive

Carbon's `DatePicker` is a **monolithic field**: `<DatePicker datePickerType>` wraps one or two `<DatePickerInput>` children, internally renders a flatpickr calendar, and manages all parsing / formatting / popover state.

shadcn does **not** ship a `DatePicker` component. It ships only the **`Calendar` primitive** (built on `react-day-picker`). The "date picker" pattern is a **manual composition**: a `<Popover>` whose trigger is a `<Button>` showing the selected date, and whose content is `<Calendar>`. You own the formatted-string ↔ `Date` conversion (typically with `date-fns`).

The wrapper file (`date-picker.shadcn.tsx`) re-exports `Calendar` and the `Popover` family for convenience; the actual composition lives in your app code.

## Underlying libraries

- **Carbon `DatePicker`**: built on **flatpickr** — full-featured, includes a built-in input parser, range mode, time picker, locale support out of the box.
- **shadcn `Calendar`**: built on **react-day-picker v9** — lower level, no input/text parsing. You bring `date-fns` (or another formatter) for input display and parsing.

## Structural differences

| Concept | Carbon | shadcn |
|---|---|---|
| Wrapper | `<DatePicker datePickerType="single">` | `<Popover>` (trigger + content composition) |
| Input element | `<DatePickerInput labelText placeholder>` | `<Button variant="outline">` showing formatted date (no `<input>` typing by default) |
| Calendar panel | implicit (flatpickr inline) | `<PopoverContent><Calendar /></PopoverContent>` |
| Single date | `datePickerType="single"` | `<Calendar mode="single" selected onSelect>` |
| Date range | `datePickerType="range"` + 2 inputs | `<Calendar mode="range" selected onSelect>` |
| Multiple dates | not supported | `<Calendar mode="multiple" selected onSelect>` |
| Display format | `dateFormat="m/d/Y"` (flatpickr tokens) | format yourself, e.g. `format(date, 'PPP')` from `date-fns` |
| Min / max date | `minDate` / `maxDate` | `disabled={[{ before: date }, { after: date }]}` (react-day-picker matchers) |
| Disabled dates | `disable` array | same — `disabled={[…matchers]}` |
| Locale | `locale="en"` (flatpickr) | `locale={enUS}` from `date-fns/locale` |
| Time picker | not built-in (use a separate `<TimePicker>`) | not built-in either — compose with `<input type="time">` |

## Carbon features missing in shadcn

- **`<DatePickerInput>` with text typing** — Carbon's input is a real `<input>` that parses typed values via flatpickr (`m/d/Y` → Date). shadcn's button trigger does NOT accept typed input. To get a typeable date field: render `<Input>` next to `<Calendar>`, parse `onChange` with `date-fns/parse`, and clamp invalid input.
- **Built-in `dateFormat` / `pattern`** — Carbon parses input against a flatpickr-style format string. shadcn requires you to format and parse manually with `date-fns`.
- **`labelText` / `helperText` / `invalidText` / `warnText`** — Carbon ships labels and messaging on `<DatePickerInput>`. shadcn forces external composition.
- **Two-input range UI** — Carbon's `datePickerType="range"` renders two inputs (start and end), each `<DatePickerInput>` is a real input that can be typed into. shadcn's range is a single button that opens a calendar with range selection; no two-input typing UI.
- **Locale via `locale` string** — Carbon accepts an ISO locale string (`"en"`, `"de"`, …). shadcn requires you to import the locale object from `date-fns/locale` and pass it to `format()` and the `<Calendar locale>` prop.
- **`light` prop** — Carbon's light-on-dark variant. shadcn relies on theme tokens.
- **`<DatePickerSkeleton>`** — Carbon ships a skeleton state. shadcn has none — use the generic `skeleton` primitive.
- **Inline (no popover) calendar variant** — Carbon's `inline` prop renders the calendar always-open. shadcn: just render `<Calendar>` directly without wrapping it in `<Popover>`.

## shadcn features missing in Carbon

- **`<Calendar>` standalone** — render the calendar grid anywhere (no popover, no input). Carbon's `<DatePicker inline>` does this but is awkward.
- **`mode="multiple"`** — multi-date selection (non-contiguous dates). Carbon has no equivalent.
- **`numberOfMonths`** — render multiple months side by side (great for range pickers). Carbon's range picker shows one month at a time.
- **`captionLayout="dropdown"` / `"dropdown-years"`** — month/year dropdowns instead of prev/next chevrons. Carbon has chevrons only.
- **`disabled={fn}`** — function-based disabled-date matchers (e.g., `(d) => d.getDay() === 0`). Carbon's `disable` is array-only.
- **`fromDate` / `toDate` constraints** — soft min/max boundaries that limit calendar navigation. Carbon's `minDate`/`maxDate` are similar but Carbon's typeable input can still receive dates outside the range.
- **Composable trigger** — the `<Button>` in `<PopoverTrigger asChild>` can be any element (icon-only, badge, etc.). Carbon's input is fixed.
- **Animation primitives** — Popover open/close animations driven by Radix's `data-state`.

## Behavioural differences

- **Underlying primitive** — Carbon: flatpickr (popover-style, click-driven). shadcn: react-day-picker inside a Radix Popover.
- **Input typing** — Carbon: parsed input. shadcn: button-only by default; typeable input is an extra composition.
- **Range UX** — Carbon: type in two separate inputs. shadcn: pick start, then pick end on a 1-or-2-month grid.
- **Default month displayed** — Carbon: today. shadcn: month of `selected` (or today if undefined).
- **Form integration** — Carbon: `<DatePickerInput>` is a real input that serializes natively. shadcn: trigger is a button — for form posts, add a hidden `<input type="hidden" name>` synced with the selected date, or use a form library.
- **Mobile UX** — Carbon: still the flatpickr popover (no native picker). shadcn: still the react-day-picker grid (no native picker). Both override mobile UX.

## Migration checklist

1. Decide whether you need a typeable input. If **yes**, the migration is significantly more work — you'll compose `<Input>` + `<Popover>` + `<Calendar>` and own the parse/format wiring with `date-fns`. If **no** (button-only is fine), the migration is straightforward.
2. Replace `<DatePicker datePickerType="single">` with the canonical recipe:
   ```tsx
   <Popover>
     <PopoverTrigger asChild>
       <Button variant="outline" className="…">
         <CalendarIcon className="mr-2 h-4 w-4" />
         {date ? format(date, 'PPP') : 'Pick a date'}
       </Button>
     </PopoverTrigger>
     <PopoverContent className="w-auto p-0">
       <Calendar mode="single" selected={date} onSelect={setDate} />
     </PopoverContent>
   </Popover>
   ```
3. For range: `<Calendar mode="range" selected={range} onSelect={setRange} numberOfMonths={2} />`. Trigger button shows formatted `from – to`.
4. Replace `dateFormat` with `format(date, fmt)` from `date-fns`. Translate flatpickr tokens to date-fns tokens (e.g., `m/d/Y` → `MM/dd/yyyy`, `M j, Y` → `LLL d, y`).
5. Replace `minDate`/`maxDate` with `disabled={[{before: minDate}, {after: maxDate}]}` (react-day-picker matchers).
6. Replace `disable={[Date1, Date2]}` with the same array under `disabled`.
7. Replace `locale="en"` with `import {enUS} from 'date-fns/locale'` and pass `locale={enUS}` to `<Calendar>` AND to your `format()` calls.
8. Move `labelText` to a sibling `<label>`, `helperText`/`invalidText` to sibling `<p>`s; toggle border colour on the trigger button via `aria-invalid` + `className`.
9. For form serialization: add `<input type="hidden" name="…" value={date?.toISOString() ?? ''} />` next to the popover, or use `react-hook-form`'s `Controller`.
10. If you used `inline`: drop the `<Popover>` wrapper — `<Calendar>` renders fine standalone.
11. If using time picker: shadcn doesn't ship one. Compose `<Input type="time">` next to the calendar, or use a third-party library like `@internationalized/date` + `react-aria-components`.
