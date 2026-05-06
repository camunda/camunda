# SkeletonIcon — Carbon → shadcn migration guide

## Generic vs. specific

Carbon ships **per-content-type skeletons**:
- `SkeletonIcon` — small square placeholder for icons.
- `SkeletonText` — line-shaped placeholder(s) for text. (Covered separately.)
- `SkeletonPlaceholder` — generic rectangle.
- Plus `Skeleton`-prefixed variants of full components (`ButtonSkeleton`, `DropdownSkeleton`, `DataTableSkeleton`, etc.).

shadcn ships **one generic `Skeleton` primitive** — a div with `animate-pulse` + `rounded-md` + `bg-accent`. You size it with Tailwind to match whatever shape is being placeheld. There are no Carbon-style "skeleton-of-component" presets; you assemble multi-element skeletons by composing several `<Skeleton>` divs.

## Mapping

| Carbon | shadcn |
|---|---|
| `<SkeletonIcon />` (16×16 default) | `<Skeleton className="size-4" />` |
| `<SkeletonIcon style={{width: 24, height: 24}} />` | `<Skeleton className="size-6" />` |
| `<SkeletonText />` (single line) | `<Skeleton className="h-4 w-full" />` (covered in `skeleton-text/`) |
| `<SkeletonPlaceholder />` | `<Skeleton className="h-32 w-full" />` |

## Differences

- **Animation** — Carbon: shimmer (gradient sliding left-to-right). shadcn: opacity pulse (`animate-pulse`). Visually different; not a perfect match.
- **Default colour** — Carbon: layer-token-driven (typically a light grey). shadcn: `bg-accent` (theme accent token, usually neutral grey).
- **Default radius** — Carbon: 0 (square). shadcn: `rounded-md` (~6px).
- **Default size** — Carbon `SkeletonIcon`: 16×16 fixed. shadcn `Skeleton`: zero-by-zero — you must size it.
- **Built-in semantics** — Carbon's skeletons render with no `role` and `aria-hidden` is up to you. shadcn: same — neither sets `role="status"`. Add `role="status" aria-busy="true"` on a parent if announcement matters.

## Migration checklist

1. Replace `<SkeletonIcon />` with `<Skeleton className="size-4" />` (16×16) — or whatever size matches your real icon (`size-5`, `size-6`, etc.).
2. If you used inline `style={{width, height}}` on `SkeletonIcon`: switch to Tailwind `size-N` or `h-N w-N` classes.
3. If you want square corners (matching Carbon look): add `className="rounded-none"`.
4. If you want the shimmer animation (matching Carbon look): the default `animate-pulse` is opacity-based. For a shimmer look, write a custom keyframe in `globals.css` and apply it via `className="animate-[shimmer_…]"`. Most projects accept the shadcn default.
5. If multiple `SkeletonIcon`s appear next to text (the typical "loading row" pattern): compose multiple `<Skeleton>` divs in a flex row instead of using component-specific skeletons:
   ```tsx
   <div className="flex items-center gap-3">
     <Skeleton className="size-4" />
     <Skeleton className="h-4 w-32" />
   </div>
   ```
6. For announcing the loading state to screen readers: wrap the skeleton group in `<div role="status" aria-busy="true" aria-label="Loading">`.
