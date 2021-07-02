# BlazingPaging

A paging library aimed at providing what Jetpack's Paging 2 and 3 are missing, while keeping most of the functionality they offer.

Main features:

 - Network pagination
 - DB pagination with support for database invalidation updates(such as when using Room)
 - DB+Network approach offers offline support
 - Custom error Type, you're no longer constrained to using Exception for error handling
 - `PagedList(val state: Flow<State>)` get loading, error and idle states directly from PagedList, *none of that Paging 3 mess of going through the adapter*
 - PagedList operators allow changing the paged data after it has been fetched but before it reaches the adapter
   - `PagedList<T>.map(transform: (T) -> R): PagedList<R>`
   - `PagedList<T>.filter(predicate: (T) -> Boolean): PagedList<T>`
   - `PagedList<T1>.combine(with: Flow<T2>, transform: (T1, T2) -> R): PagedList<R>`
	   - Combine your PagedList with a Flow of loading ids and you can show loading indicators in each item, check out [sample](https://github.com/aromano272/BlazingPaging/blob/d9c928bd974df7ffdab3b4d32be35aa6c7142199/sample/src/main/java/com/andreromano/blazingpaging/sample/database/DatabaseFragment.kt).
	   - Combine with a filter Flow and you can filter our paged items in reactively.
