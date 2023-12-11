package com.lolo.io.onelist.feature.lists

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.NinePatchDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.h6ah4i.android.widget.advrecyclerview.animator.DraggableItemAnimator
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager
import com.h6ah4i.android.widget.advrecyclerview.swipeable.RecyclerViewSwipeManager
import com.h6ah4i.android.widget.advrecyclerview.touchguard.RecyclerViewTouchActionGuardManager
import com.lolo.io.onelist.MainActivity
import com.lolo.io.onelist.R
import com.lolo.io.onelist.core.model.Item
import com.lolo.io.onelist.core.model.ItemList
import com.lolo.io.onelist.core.ui.Config
import com.lolo.io.onelist.core.ui.util.BUTTON_ANIM_DURATION
import com.lolo.io.onelist.core.ui.util.afterTextChanged
import com.lolo.io.onelist.core.ui.util.animHideFlip
import com.lolo.io.onelist.core.ui.util.animShowFlip
import com.lolo.io.onelist.core.ui.util.animTranslation
import com.lolo.io.onelist.core.ui.util.dpToPx
import com.lolo.io.onelist.core.ui.util.flipX
import com.lolo.io.onelist.core.ui.util.isVisible
import com.lolo.io.onelist.core.ui.util.isVisibleInvisible
import com.lolo.io.onelist.core.ui.util.shake
import com.lolo.io.onelist.databinding.FragmentOneListBinding
import com.lolo.io.onelist.feature.lists.dialogs.ACTION_RM_FILE
import com.lolo.io.onelist.feature.lists.dialogs.deleteListDialog
import com.lolo.io.onelist.feature.lists.dialogs.editItemDialog
import com.lolo.io.onelist.feature.lists.dialogs.editListDialog
import com.lolo.io.onelist.feature.lists.lists_adapters.ItemTouchHelperCallback
import com.lolo.io.onelist.feature.lists.lists_adapters.ItemsAdapter
import com.lolo.io.onelist.feature.lists.lists_adapters.ItemsCallbacks
import com.lolo.io.onelist.feature.lists.lists_adapters.ListsAdapter
import com.lolo.io.onelist.feature.lists.lists_adapters.ListsCallbacks
import com.lolo.io.onelist.feature.settings.SettingsFragment
import com.skydoves.powermenu.MenuAnimation
import com.skydoves.powermenu.PowerMenu
import com.skydoves.powermenu.PowerMenuItem
import com.skydoves.powermenu.kotlin.createPowerMenu
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.getViewModel

class OneListFragment : Fragment(), ListsCallbacks, ItemsCallbacks,
    MainActivity.OnDispatchTouchEvent {

    companion object {
        const val ARG_EXT_FILE_URI = "EXT_FILE_URI"
    }

    private var _binding: FragmentOneListBinding? = null
    private val binding: FragmentOneListBinding
        get() = _binding!!

    private val viewModel
            by lazy { getViewModel<OneListFragmentViewModel>() }

    private val _fragmentAllListsFinalInstance = mutableListOf<ItemList>()

    private var container: ViewGroup? = null

    private val listsAdapter: ListsAdapter by lazy { ListsAdapter(requireActivity(), _fragmentAllListsFinalInstance, this) }
    private val itemsAdapter by lazy { ItemsAdapter(requireActivity(), this) }

    private val isAddCommentShown
        get() = binding.addCommentEditText.height > 0

    private val popupMenu: PowerMenu by lazy {
        requireContext().let {
            createPowerMenu(it) {
                addItem(
                    PowerMenuItem(
                        getString(R.string.settings),
                        R.drawable.ic_settings_accent_24dp
                    )
                )
                setAnimation(MenuAnimation.SHOWUP_TOP_LEFT)
                setMenuRadius(10f)
                setMenuShadow(10f)
                setTextGravity(Gravity.START)
                setTextTypeface(Typeface.DEFAULT)
                setTextColor(ContextCompat.getColor(it, R.color.textColorPrimary))
                setMenuColor(ContextCompat.getColor(it, R.color.colorBackgroundPopup))
                setShowBackground(false)
                setAutoDismiss(true)
                setOnMenuItemClickListener { _, _ ->


                    parentFragmentManager.beginTransaction()
                        .setCustomAnimations(
                            R.anim.enter_from_right,
                            R.anim.zoom_out,
                            R.anim.zoom_in,
                            R.anim.exit_to_right
                        )
                        .add(container?.id ?: 0, SettingsFragment())
                        .hide(this@OneListFragment)
                        .addToBackStack(null).commit()
                }
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        activity?.let {
            it.packageManager.getPackageInfo(it.packageName, 0).versionName
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOneListBinding.inflate(inflater, container, false)
        this.container = container
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        setupListsRecyclerView()
        setupItemsRecyclerView()

        lifecycleScope.launch {
            viewModel.init()
            if (_fragmentAllListsFinalInstance.size != viewModel.allLists.value.size) {
                listsAdapter.notifyItemRangeInserted(0, viewModel.allLists.value.size)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allLists.collect {
                    _fragmentAllListsFinalInstance.clear()
                    _fragmentAllListsFinalInstance.addAll(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedList.collect {
                    itemsAdapter.items = it.items
                    listsAdapter.selectList(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.newListImportedTrigger.collect {
                    _fragmentAllListsFinalInstance.clear()
                    _fragmentAllListsFinalInstance.addAll(viewModel.allLists.value)
                    listsAdapter.notifyItemInserted(viewModel.allLists.value.size - 1)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect {
                    updateUI(it)
                }
            }
        }

        binding.addItemEditText.setOnEditorActionListener { tv, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addItem(Item(tv.text.toString()))
            }
            true
        }

        binding.validate.setOnClickListener {
            addItem(Item(binding.addItemEditText.text.toString()))
        }

        binding.buttonAddList.setOnClickListener {
            editListDialog(requireContext()) { list ->
                createList(
                    list
                )
            }.show()
        }

        binding.buttonEditList.setOnClickListener { editList() }
        binding.buttonRemoveList.setOnClickListener {
            showDeleteDialog(viewModel.selectedList.value)
        }
        binding.buttonAddComment.setOnClickListener { switchCommentSection() }
        binding.buttonClearComment.setOnClickListener { viewModel.clearComment() }
        binding.buttonShareList.setOnClickListener { viewModel.shareSelectedList(this.requireContext()) }


        binding.menuArrow.setOnClickListener { v ->
            popupMenu.showAsAnchorLeftTop(v, dpToPx(12), dpToPx(12))
        }

        binding.swipeContainer.setOnRefreshListener {
            viewModel.refreshAllLists()
        }


        binding.addItemEditText.afterTextChanged {
            viewModel.setAddItemText(it)
        }

        binding.addCommentEditText.afterTextChanged { text ->
            viewModel.setAddItemComment(text)
        }


    }


    private fun updateUI(uiState: UIState) {
        binding.swipeContainer.isRefreshing = uiState.isRefreshing
        binding.validate.isVisibleInvisible = uiState.showValidate
        binding.buttonAddComment.isVisibleInvisible = uiState.showAddCommentArrow
        if (uiState.addCommentText.isEmpty()) {
            binding.addCommentEditText.setText(uiState.addCommentText)
        }
        binding.buttonClearComment.isVisible = uiState.showButtonClearComment
    }


    override fun onResume() {
        super.onResume()

        if (arguments?.containsKey(ARG_EXT_FILE_URI) == true) {
            lifecycleScope.launch {
                try {
                    val imported = viewModel.importList(
                        Uri.parse(
                            arguments?.getString(ARG_EXT_FILE_URI)
                                ?: throw IllegalArgumentException("uri must not be null")
                        )
                    )

                    Toast.makeText(
                        activity,
                        getString(R.string.list_copied, imported.title),
                        Toast.LENGTH_LONG
                    ).show()

                } catch (e: Exception) {
                    Toast.makeText(activity, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        arguments?.clear()
        viewModel.refreshAllLists()
    }

    private fun setupListsRecyclerView() {
        binding.listsRecyclerView.adapter = listsAdapter
        val layoutManager = FlexboxLayoutManager(context)
        layoutManager.flexDirection = FlexDirection.ROW
        layoutManager.justifyContent = JustifyContent.CENTER
        layoutManager.flexWrap = FlexWrap.WRAP
        binding.listsRecyclerView.layoutManager = layoutManager
        binding.listsRecyclerView.itemAnimator = DefaultItemAnimator()
        val listCallback = ItemTouchHelperCallback(listsAdapter)
        val listTouchHelper = ItemTouchHelper(listCallback)
        listTouchHelper.attachToRecyclerView(binding.listsRecyclerView)
    }

    private fun setupItemsRecyclerView() {
        val itemsDragDropManager = RecyclerViewDragDropManager()
        val itemsSwipeManager = RecyclerViewSwipeManager()
        var wrappedAdapter = itemsDragDropManager.createWrappedAdapter(itemsAdapter)
        wrappedAdapter = itemsSwipeManager.createWrappedAdapter(wrappedAdapter)
        binding.itemsRecyclerView.adapter = wrappedAdapter
        binding.itemsRecyclerView.layoutManager = LinearLayoutManager(context)
        val itemsTouchActionGuardManager = RecyclerViewTouchActionGuardManager()
        itemsTouchActionGuardManager.setInterceptVerticalScrollingWhileAnimationRunning(true)
        itemsTouchActionGuardManager.isEnabled = true
        itemsTouchActionGuardManager.attachRecyclerView(binding.itemsRecyclerView)
        itemsSwipeManager.attachRecyclerView(binding.itemsRecyclerView)
        itemsDragDropManager.attachRecyclerView(binding.itemsRecyclerView)
        itemsDragDropManager.setInitiateOnLongPress(true)
        itemsDragDropManager.setInitiateOnMove(false)
        itemsDragDropManager.setDraggingItemShadowDrawable(
            ContextCompat.getDrawable(
                requireContext(),
                R.drawable.material_shadow_z3
            ) as NinePatchDrawable?
        )
        val animator = DraggableItemAnimator()
        animator.supportsChangeAnimations = false
        binding.itemsRecyclerView.itemAnimator = animator
        if (Config.smallScreen) {
            val dividerItemDecoration = DividerItemDecoration(
                binding.itemsRecyclerView.context,
                (binding.itemsRecyclerView.layoutManager as LinearLayoutManager).orientation
            )
            binding.itemsRecyclerView.addItemDecoration(dividerItemDecoration)
        }
    }


    // Lists handlers
    private fun createList(itemList: ItemList) {
        listsAdapter.notifyItemInserted(viewModel.allLists.value.size)
        viewModel.createList(itemList)
        binding.addItemEditText.requestFocus()
    }

    private fun editList() {
        editListDialog(requireContext(), viewModel.selectedList.value) { list ->
            viewModel.editList(list)
            listsAdapter
                .notifyItemChanged(viewModel.allLists.value.indexOf(viewModel.selectedList.value))
            hideEditionButtons()
        }.show()
    }


    private fun showDeleteDialog(itemList: ItemList) {
        deleteListDialog(requireContext(), itemList) { action ->
            itemsAdapter
                .notifyItemRangeRemoved(0, viewModel.selectedList.value.items.size)
            listsAdapter.notifyItemRemoved(viewModel.allLists.value.indexOf(itemList))
            viewModel.removeList(itemList, action and ACTION_RM_FILE != 0)
            hideEditionButtons()
        }.show()
    }

    override fun onSelectList(position: Int) {

        itemsAdapter.notifyItemRangeRemoved(
            0,
            viewModel.selectedList.value.items.size
        )

        viewModel.selectList(position)

        itemsAdapter.notifyItemRangeInserted(
            0,
            viewModel.allLists.value[position].items.size
        )
    }

    override fun onListAdapterStartDrag() = showEditionButtons()
    override fun onListMoved(fromPosition: Int, toPosition: Int) {
        if (binding.buttonRemoveList.alpha == 1F)
            hideEditionButtons()
        viewModel.moveList(fromPosition, toPosition)
        listsAdapter.notifyItemMoved(fromPosition, toPosition)
    }


    // Items handlers :
    private fun addItem(item: Item) {
        if (item.title.isNotEmpty()) {
            if (viewModel.allLists.value.isEmpty()) {
                createList(ItemList(title = getString(R.string.list_default_name)))
            }
            viewModel.addItem(item)
            val position = viewModel.selectedList.value.items.indexOf(item)

            itemsAdapter.notifyItemInserted(position)
            binding.itemsRecyclerView.smoothScrollToPosition(0)
            binding.addItemEditText.setText(R.string.empty)
            binding.addItemEditText.requestFocus()

            if (binding.addCommentEditText.text.isEmpty() && isAddCommentShown) {
                switchCommentSection()
            }

        } else listOf(binding.addItemEditText, binding.validate).forEach { it.shake() }
    }

    override fun onRemoveItem(item: Item) {
        viewModel.removeItem(item)
        itemsAdapter.notifyItemRemoved(viewModel.selectedList.value.items.indexOf(item))
    }

    override fun openEditItemDialog(index: Int) {
        editItemDialog(requireContext(), viewModel.selectedList.value.items[index]) { updatedItem ->
            viewModel.editItem(index, updatedItem)
            itemsAdapter.notifyItemChanged(index)
        }.show()
    }

    override fun onSwitchItemStatus(item: Item) {
        viewModel.switchItemStatus(item) { oldPosition, newPosition ->
            itemsAdapter.notifyItemChanged(oldPosition)
            itemsAdapter.notifyItemMoved(oldPosition, newPosition)

            val scrolledToTop =
                (binding.itemsRecyclerView.layoutManager as LinearLayoutManager)
                    .findFirstVisibleItemPosition() == 0
            if (scrolledToTop || oldPosition == 0) binding.itemsRecyclerView
                .scrollToPosition(0)
        }
    }

    override fun onShowOrHideComment(item: Item) {
        viewModel.showOrHideComment(item)
        itemsAdapter.notifyItemChanged(viewModel.selectedList.value.items.indexOf(item))
    }

    override fun onMoveItem(fromPosition: Int, toPosition: Int) {
        viewModel.moveItem(fromPosition, toPosition)
    }

    // hide keyboard when touch outside an EditText
    override fun onDispatchTouchEvent(ev: MotionEvent) {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val addItemRect = Rect()
            val addCommentRect = Rect()
            val showCommentRect = Rect()
            binding.addItemEditText.getGlobalVisibleRect(addItemRect)
            binding.addCommentEditText.getGlobalVisibleRect(addCommentRect)
            binding.buttonAddComment.getGlobalVisibleRect(showCommentRect)
            val view = activity?.currentFocus
            val rawX = ev.rawX.toInt()
            val rawY = ev.rawY.toInt()
            if (view != null && view is EditText) {
                val r = Rect()
                view.getGlobalVisibleRect(r)
                if (!addItemRect.contains(rawX, rawY) && !addCommentRect.contains(
                        rawX,
                        rawY
                    ) && !showCommentRect.contains(rawX, rawY)
                ) {
                    view.clearFocus()
                    val inputMethodManager =
                        activity?.application?.getSystemService(Activity.INPUT_METHOD_SERVICE)
                                as InputMethodManager
                    inputMethodManager
                        .hideSoftInputFromWindow(binding.rootView.windowToken, 0)
                }
            }

            val rectButtons = Rect()
            binding.buttonsLayout.getGlobalVisibleRect(rectButtons)

            if (binding.buttonRemoveList.alpha == 1F && !rectButtons.contains(rawX, rawY))
                hideEditionButtons()
        }
    }

    private fun switchCommentSection() {
        binding.addCommentEditText.measure(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        val measuredHeight = binding.addCommentEditText.measuredHeight
        val height = binding.addCommentEditText.height
        val commentSlideAnimation =
            ValueAnimator.ofInt(height, measuredHeight - height).setDuration(BUTTON_ANIM_DURATION)
        commentSlideAnimation.addUpdateListener { animation ->
            binding.addCommentEditText.layoutParams.height = animation.animatedValue as Int
            binding.addCommentEditText.requestLayout()
            binding.buttonClearComment.visibility = View.GONE
            if (animation.animatedValue == measuredHeight && animation.animatedValue as Int > 0) {
                binding.addCommentEditText.layoutParams.height =
                    RelativeLayout.LayoutParams.WRAP_CONTENT
                if (binding.addCommentEditText.text.isNotEmpty()) {
                    binding.buttonClearComment.visibility =
                        View.VISIBLE
                }
            }
        }
        commentSlideAnimation.start()

        binding.buttonAddComment.flipX()

        val editText = if (!isAddCommentShown) binding.addCommentEditText
        else binding.addItemEditText
        editText.requestFocus()

        val imm = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun showEditionButtons() {
        binding.buttonShareList.visibility = View.GONE
        binding.buttonRemoveList.animShowFlip()
        binding.buttonAddList.animHideFlip(startDelay = BUTTON_ANIM_DURATION)
        binding.buttonEditList.animShowFlip()
        binding.buttonEditList.animTranslation(
            stopX = dpToPx(-32).toFloat(),
            startDelay = BUTTON_ANIM_DURATION
        )
    }

    private fun hideEditionButtons() {
        binding.buttonShareList.visibility = View.VISIBLE
        binding.buttonAddList.animShowFlip()
        binding.buttonRemoveList.animHideFlip(startDelay = BUTTON_ANIM_DURATION)
        binding.buttonEditList.animHideFlip()
        binding.buttonEditList.animTranslation(dpToPx(-32).toFloat())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}