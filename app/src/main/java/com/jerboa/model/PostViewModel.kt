package com.jerboa.model

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import com.jerboa.*
import com.jerboa.api.API
import com.jerboa.api.ApiState
import com.jerboa.api.apiWrapper
import com.jerboa.datatypes.types.*
import com.jerboa.db.Account
import com.jerboa.ui.components.common.Initializable
import kotlinx.coroutines.launch

const val COMMENTS_DEPTH_MAX = 6

class PostViewModel : ViewModel(), Initializable {
    override var initialized by mutableStateOf(false)

    var postRes: ApiState<GetPostResponse> by mutableStateOf(ApiState.Empty)
        private set

    var commentsRes: ApiState<GetCommentsResponse> by mutableStateOf(ApiState.Empty)
        private set

    // If this is set, its a comment type view
    var id by mutableStateOf<Either<PostId, CommentId>?>(null)
        private set
    var sortType by mutableStateOf(CommentSortType.Hot)
        private set

    private var likeCommentRes: ApiState<CommentResponse> by mutableStateOf(ApiState.Empty)
    private var saveCommentRes: ApiState<CommentResponse> by mutableStateOf(ApiState.Empty)
    private var deleteCommentRes: ApiState<CommentResponse> by mutableStateOf(ApiState.Empty)

    private var likePostRes: ApiState<PostResponse> by mutableStateOf(ApiState.Empty)
    private var savePostRes: ApiState<PostResponse> by mutableStateOf(ApiState.Empty)
    private var deletePostRes: ApiState<PostResponse> by mutableStateOf(ApiState.Empty)
    private var blockCommunityRes: ApiState<BlockCommunityResponse> by mutableStateOf(ApiState.Empty)
    private var blockPersonRes: ApiState<BlockPersonResponse> by mutableStateOf(ApiState.Empty)
    private var markPostRes: ApiState<PostResponse> by mutableStateOf(ApiState.Empty)

    fun initialize(
        id: Either<PostId, CommentId>,
    ) {
        this.id = id
    }

    fun updateSortType(sortType: CommentSortType) {
        this.sortType = sortType
    }

    fun getData(
        account: Account?,
        state: ApiState<GetPostResponse> = ApiState.Loading,
    ) {
        viewModelScope.launch {
            // Set the commentId for the right case
            id?.also { id ->

                val postForm = id.fold({
                    GetPost(id = it, auth = account?.jwt)
                }, {
                    GetPost(comment_id = it, auth = account?.jwt)
                })

                postRes = state
                postRes = apiWrapper(API.getInstance().getPost(postForm.serializeToMap()))

                val commentsForm = id.fold({
                    GetComments(
                        max_depth = COMMENTS_DEPTH_MAX,
                        type_ = ListingType.All,
                        post_id = it,
                        auth = account?.jwt,
                        sort = sortType,
                    )
                }, {
                    GetComments(
                        max_depth = COMMENTS_DEPTH_MAX,
                        type_ = ListingType.All,
                        parent_id = it,
                        auth = account?.jwt,
                        sort = sortType,
                    )
                })

                commentsRes = ApiState.Loading
                commentsRes =
                    apiWrapper(API.getInstance().getComments(commentsForm.serializeToMap()))
            }
        }
    }

    fun isCommentView(): Boolean {
        return id?.isRight() ?: false
    }

    fun fetchMoreChildren(
        commentView: CommentView,
        account: Account?,
    ) {
        viewModelScope.launch {
            val existing = commentsRes
            when (existing) {
                is ApiState.Success -> commentsRes = ApiState.Appending(existing.data)
                else -> return@launch
            }

            val commentsForm = GetComments(
                parent_id = commentView.comment.id,
                max_depth = COMMENTS_DEPTH_MAX,
                type_ = ListingType.All,
                auth = account?.jwt,
            )

            val moreComments =
                apiWrapper(API.getInstance().getComments(commentsForm.serializeToMap()))

            when (moreComments) {
                is ApiState.Success -> {
                    // Remove the first comment, since it is a parent
                    val newComments = moreComments.data.comments.toMutableList()
                    newComments.removeAt(0)

                    val appended = appendData(existing.data.comments, newComments.toList())

                    commentsRes = ApiState.Success(existing.data.copy(comments = appended))
                }

                else -> {}
            }
        }
    }

    fun likeComment(form: CreateCommentLike) {
        viewModelScope.launch {
            likeCommentRes = ApiState.Loading
            likeCommentRes = apiWrapper(API.getInstance().likeComment(form))

            when (val likeRes = likeCommentRes) {
                is ApiState.Success -> {
                    updateComment(likeRes.data.comment_view)
                }

                else -> {}
            }
        }
    }

    fun deleteComment(form: DeleteComment) {
        viewModelScope.launch {
            deleteCommentRes = ApiState.Loading
            deleteCommentRes = apiWrapper(API.getInstance().deleteComment(form))

            when (val deleteRes = deleteCommentRes) {
                is ApiState.Success -> {
                    updateComment(deleteRes.data.comment_view)
                }

                else -> {}
            }
        }
    }

    fun saveComment(form: SaveComment) {
        viewModelScope.launch {
            saveCommentRes = ApiState.Loading
            saveCommentRes = apiWrapper(API.getInstance().saveComment(form))

            when (val saveRes = saveCommentRes) {
                is ApiState.Success -> {
                    updateComment(saveRes.data.comment_view)
                }

                else -> {}
            }
        }
    }

    fun likePost(form: CreatePostLike) {
        viewModelScope.launch {
            likePostRes = ApiState.Loading
            likePostRes = apiWrapper(API.getInstance().likePost(form))

            when (val likeRes = likePostRes) {
                is ApiState.Success -> {
                    updatePost(likeRes.data.post_view)
                }

                else -> {}
            }
        }
    }

    fun savePost(form: SavePost) {
        viewModelScope.launch {
            savePostRes = ApiState.Loading
            savePostRes = apiWrapper(API.getInstance().savePost(form))
            when (val saveRes = savePostRes) {
                is ApiState.Success -> {
                    updatePost(saveRes.data.post_view)
                }

                else -> {}
            }
        }
    }

    fun deletePost(form: DeletePost) {
        viewModelScope.launch {
            deletePostRes = ApiState.Loading
            deletePostRes = apiWrapper(API.getInstance().deletePost(form))
            when (val deletePost = deletePostRes) {
                is ApiState.Success -> {
                    updatePost(deletePost.data.post_view)
                }

                else -> {}
            }
        }
    }

    fun blockCommunity(form: BlockCommunity, ctx: Context) {
        viewModelScope.launch {
            blockCommunityRes = ApiState.Loading
            blockCommunityRes =
                apiWrapper(API.getInstance().blockCommunity(form))
            showBlockCommunityToast(blockCommunityRes, ctx)
        }
    }

    fun blockPerson(form: BlockPerson, ctx: Context) {
        viewModelScope.launch {
            blockPersonRes = ApiState.Loading
            blockPersonRes = apiWrapper(API.getInstance().blockPerson(form))
            showBlockPersonToast(blockPersonRes, ctx)
        }
    }

    fun updateComment(commentView: CommentView) {
        when (val existing = commentsRes) {
            is ApiState.Success -> {
                val newComments =
                    findAndUpdateComment(
                        existing.data.comments,
                        commentView,
                    )
                val newRes =
                    ApiState.Success(existing.data.copy(comments = newComments))
                commentsRes = newRes
            }

            else -> {}
        }
    }

    // TODO test this to make sure comment tree inserts work
    fun appendComment(commentView: CommentView) {
        when (val existing = commentsRes) {
            is ApiState.Success -> {
                val mutable = existing.data.comments.toMutableList()
                mutable.add(commentView)
                val newRes =
                    ApiState.Success(existing.data.copy(comments = mutable.toList()))
                commentsRes = newRes
            }

            else -> {}
        }
    }

    fun updatePost(postView: PostView) {
        when (val existing = postRes) {
            is ApiState.Success -> {
                val newRes = ApiState.Success(existing.data.copy(post_view = postView))
                postRes = newRes
            }

            else -> {}
        }
    }

    fun markPostAsRead(form: MarkPostAsRead) {
        viewModelScope.launch {
            markPostRes = ApiState.Loading
            markPostRes = apiWrapper(API.getInstance().markAsRead(form))

            when (val markRes = markPostRes) {
                is ApiState.Success -> {
                    updatePost(markRes.data.post_view)
                }

                else -> {}
            }
        }
    }
}
