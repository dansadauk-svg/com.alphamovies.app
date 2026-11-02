# Alpha Movies Android v1.0.10

## Java compilation fix

- Adds `androidx.swiperefreshlayout:swiperefreshlayout:1.1.0`.
- Excludes obsolete `ng/com/mkmovies/**` sources that may remain after merging an Alpha Movies upload into an older GitHub repository.
- Preserves the correct `com.alphamovies.app` namespace and application ID.
- Preserves generated `R` and `BuildConfig` in the Alpha Movies namespace.
- Keeps BuildConfig generation enabled.
- Retains the Android resource fix from v1.0.9.
- Retains clean movie-title download notifications from v1.0.8.
