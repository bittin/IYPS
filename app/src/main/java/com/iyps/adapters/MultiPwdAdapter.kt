/*
 *     Copyright (C) 2022-present StellarSand
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.iyps.adapters

import androidx.recyclerview.widget.RecyclerView
import com.iyps.adapters.MultiPwdAdapter.ListViewHolder
import android.widget.TextView
import com.iyps.R
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.View
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import me.stellarsand.android.fastscroll.PopupTextProvider

class MultiPwdAdapter(
    private val clickListener: OnItemClickListener
): PagingDataAdapter<String, ListViewHolder>(DIFF_CALLBACK), PopupTextProvider {
    
    private companion object {
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<String>() {
                override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
                    return oldItem == newItem
                }
                override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
                    return oldItem == newItem
                }
            }
    }
    
    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }
    
    inner class ListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
        
        val passwordLine: TextView = itemView.findViewById(R.id.password_line)
        
        init {
            // Handle click events of items
            itemView.setOnClickListener(this)
        }
        
        override fun onClick(v: View?) {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                clickListener.onItemClick(position)
            }
        }
        
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_multi_pwd_rv, parent, false)
        return ListViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
        holder.passwordLine.text = getItem(position)!!
    }
    
    override fun getPopupText(view: View, position: Int): CharSequence {
        return getItem(position)!!.first().uppercaseChar().toString()
    }
}