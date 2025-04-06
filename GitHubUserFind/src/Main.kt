import java.net.HttpURLConnection
import java.net.URL
import java.util.Scanner

// main function to show menu and scan the desire option
fun main() {
    val scanner = Scanner(System.`in`)
    val cache = mutableMapOf<String, GitHubUser>()

    while (true) {
        println("\nGitHub User Fetcher")
        println("1. Get user info by username")
        println("2. Show cached users")
        println("3. Search cached users")
        println("4. Search repositories")
        println("5. Exit")
        print("Choose option: ")

        when (scanner.nextLine()) {
            "1" -> fetchUser(scanner, cache)
            "2" -> showCachedUsers(cache)
            "3" -> searchUsers(scanner, cache)
            "4" -> searchRepos(scanner, cache)
            "5" -> return
            else -> println("Invalid option!")
        }
    }
}

// Data classes
data class GitHubUser(
    val login: String,
    val followers: Int,
    val following: Int,
    val createdAt: String,
    val repos: List<Repo> = emptyList()
)

data class Repo(
    val name: String,
    val description: String?,
    val language: String?,
    val stars: Int,
    val forks: Int
)


// parse repo and user Json to desire elements
fun parseUserJson(json: String): GitHubUser {
    fun getString(key: String): String {
        val keyIndex = json.indexOf("\"$key\"")
        if (keyIndex == -1) return ""
        val start = json.indexOf(':', keyIndex) + 1
        val end = json.indexOf(',', start).let { if (it == -1) json.indexOf('}', start) else it }
        return json.substring(start, end).trim().trim('"', ' ')
    }

    fun getInt(key: String): Int = getString(key).toIntOrNull() ?: 0

    return GitHubUser(
        login = getString("login"),
        followers = getInt("followers"),
        following = getInt("following"),
        createdAt = getString("created_at"),
        repos = emptyList()
    )
}

fun parseReposJson(json: String): List<Repo> {
    val repos = mutableListOf<Repo>()
    var currentIndex = 0

    while (true) {
        val repoStart = json.indexOf('{', currentIndex)
        if (repoStart == -1) break

        val repoEnd = json.indexOf('}', repoStart)
        if (repoEnd == -1) break

        val repoJson = json.substring(repoStart, repoEnd + 1)

        fun getString(key: String): String? {
            val keyIndex = repoJson.indexOf("\"$key\"")
            if (keyIndex == -1) return null
            val start = repoJson.indexOf(':', keyIndex) + 1
            val end = repoJson.indexOf(',', start).let { if (it == -1) repoJson.indexOf('}', start) else it }
            val value = repoJson.substring(start, end).trim().trim('"', ' ')
            return if (value == "null") null else value
        }

        fun getInt(key: String): Int = getString(key)?.toIntOrNull() ?: 0

        repos.add(Repo(
            name = getString("name") ?: "",
            description = getString("description"),
            language = getString("language"),
            stars = getInt("stargazers_count"),
            forks = getInt("forks_count")
        ))

        currentIndex = repoEnd + 1
    }

    return repos
}

// fetch from urls
fun fetchGitHubUser(username: String): GitHubUser? {
    return try {
        val userJson = fetchUrl("https://api.github.com/users/$username")
        val reposJson = fetchUrl("https://api.github.com/users/$username/repos")

        val user = parseUserJson(userJson)
        val repos = parseReposJson(reposJson)

        user.copy(repos = repos)
    } catch (e: Exception) {
        println("Error: ${e.message}")
        null
    }
}

fun fetchUrl(urlString: String): String {
    val url = URL(urlString)
    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

    if (connection.responseCode != 200) {
        throw Exception("API error: ${connection.responseCode} ${connection.responseMessage}")
    }

    return connection.inputStream.bufferedReader().use { it.readText() }
}

fun fetchUser(scanner: Scanner, cache: MutableMap<String, GitHubUser>) {
    print("Enter GitHub username: ")
    val username = scanner.nextLine().trim()

    if (username.isEmpty()) {
        println("Username cannot be empty!")
        return
    }

    println("Fetching data...")
    val user = fetchGitHubUser(username)
    if (user != null) {
        cache[username.lowercase()] = user
        printUser(user)
    }
}

fun showCachedUsers(cache: Map<String, GitHubUser>) {
    if (cache.isEmpty()) {
        println("No users in cache")
        return
    }

    println("\nCached users:")
    cache.values.forEachIndexed { i, user ->
        println("${i + 1}. ${user.login} (${user.followers} followers)")
    }
}

fun searchUsers(scanner: Scanner, cache: Map<String, GitHubUser>) {
    print("Enter username to search: ")
    val query = scanner.nextLine().trim().lowercase()

    val results = cache.filterKeys { it.contains(query) }.values
    if (results.isEmpty()) {
        println("No users found")
    } else {
        results.forEach { printUser(it) }
    }
}

fun searchRepos(scanner: Scanner, cache: Map<String, GitHubUser>) {
    print("Enter repository name to search: ")
    val query = scanner.nextLine().trim().lowercase()

    val results = mutableListOf<Pair<GitHubUser, Repo>>()
    cache.values.forEach { user ->
        user.repos.filter { it.name.lowercase().contains(query) }
            .forEach { repo -> results.add(user to repo) }
    }

    if (results.isEmpty()) {
        println("No repositories found")
    } else {
        results.forEach { (user, repo) ->
            println("\nRepository: ${repo.name}")
            println("Owner: ${user.login}")
            println("Language: ${repo.language ?: "Unknown"}")
            println("Stars: ${repo.stars}, Forks: ${repo.forks}")
        }
    }
}

fun printUser(user: GitHubUser) {
    println("\nUser: ${user.login}")
    println("Followers: ${user.followers}")
    println("Following: ${user.following}")
    println("Created: ${user.createdAt}")

    val filteredRepos = user.repos
        .filter { it.name.isNotBlank() }
        .sortedBy { it.name.lowercase() }
    println("\nRepositories (${filteredRepos.size}):")
    filteredRepos.forEachIndexed { i, repo ->
        println("${i + 1}. ${repo.name} (${repo.language ?: "No language"})")
    }
}