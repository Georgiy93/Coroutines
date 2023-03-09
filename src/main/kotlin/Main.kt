import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class PostWithComments(
    val author: Author,
    val post: Post,
    val comments: List<Comment>

)
data class Post(
    val id: Long,
    val authorId: Long,
    val content: String,
    val published: Long,
    val likedByMe: Boolean,
    val likes: Int = 0,
    var attachment: Attachment? = null,
)

data class Attachment(
    val url: String,
    val description: String,

    )

data class Comment(
    val id: Long,
    val postId: Long,
    val authorId: Long,
    val content: String,
    val published: Long,
    val likedByMe: Boolean,
    val likes: Int = 0,
)

data class Author(
    val id: Long,
    val name: String,
    val avatar: String,
)

fun main() {

    CoroutineScope(EmptyCoroutineContext).launch {

        val posts = getAll()
        val result = posts.map { async { PostWithComments(getAuthors(it.authorId), it,getComments(it.id)) }}.awaitAll()
        println(result)
    }
    Thread.sleep(3_000)
}

private val logger = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY
}
private val client = OkHttpClient.Builder()
    .addInterceptor(HttpLoggingInterceptor().apply { level=HttpLoggingInterceptor.Level.BODY })
    .connectTimeout(30, TimeUnit.SECONDS)

    .build()
private const val Base_URL = "http://localhost:9999/api/slow/"
private val Base_URL_Author="http://localhost:9999/api/"
private val gson = Gson()
suspend fun getAll(): List<Post> = parseResponse("${Base_URL}posts", object : TypeToken<List<Post>>() {})
suspend fun getAuthors(authorId:Long):Author = parseResponse("${Base_URL_Author}authors/${authorId}", object : TypeToken<Author>() {})
suspend fun getComments(postId: Long):List<Comment> = parseResponse(
    "${Base_URL}posts/${postId}/comments", object : TypeToken<List<Comment>>() {})
suspend fun <T> parseResponse(url: String, typeToken: TypeToken<T>): T {
    val response = makeRequest(url)
    return withContext(Dispatchers.Default) { gson.fromJson(requireNotNull(response.body).string(), typeToken.type) }
}

suspend fun makeRequest(url: String): Response =
    suspendCoroutine { continuation ->
        client.newCall(
            Request.Builder()
                .url(url)
                .build()
        )
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }


            })
    }
