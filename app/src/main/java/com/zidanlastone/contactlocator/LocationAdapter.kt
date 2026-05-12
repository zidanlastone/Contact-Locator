package com.zidanlastone.contactlocator

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class LocationAdapter(private var items: List<LocationItem>) :
    RecyclerView.Adapter<LocationAdapter.LocationViewHolder>() {

    class LocationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvId: TextView = view.findViewById(R.id.tvId)
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvAddress: TextView = view.findViewById(R.id.tvAddress)
        val tvContact: TextView = view.findViewById(R.id.tvContact)
        val btnOpenMaps: Button = view.findViewById(R.id.btnOpenMaps)
        val btnWhatsApp: Button = view.findViewById(R.id.btnWhatsApp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_location, parent, false)
        return LocationViewHolder(view)
    }

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        holder.tvId.text = context.getString(R.string.label_id) + item.id
        holder.tvName.text = context.getString(R.string.label_name) + item.name
        
        if (!item.address.isNullOrEmpty()) {
            holder.tvAddress.visibility = View.VISIBLE
            holder.tvAddress.text = item.address
        } else {
            holder.tvAddress.visibility = View.GONE
        }
        
        if (!item.contact.isNullOrEmpty()) {
            holder.tvContact.visibility = View.VISIBLE
            holder.btnWhatsApp.visibility = View.VISIBLE
            holder.tvContact.text = context.getString(R.string.label_contact) + item.contact
        } else {
            holder.tvContact.visibility = View.GONE
            holder.btnWhatsApp.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(context, LocationDetailActivity::class.java)
            intent.putExtra("location_item", item)
            context.startActivity(intent)
        }

        holder.btnOpenMaps.setOnClickListener {
            if (item.latitude != null && item.longitude != null) {
                val mapUri = Uri.parse("geo:${item.latitude},${item.longitude}?q=${item.latitude},${item.longitude}(${item.name})")
                val intent = Intent(Intent.ACTION_VIEW, mapUri)
                intent.setPackage("com.google.android.apps.maps")
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, mapUri))
                }
            } else {
                Toast.makeText(context, "Location coordinates not available", Toast.LENGTH_SHORT).show()
            }
        }

        holder.btnWhatsApp.setOnClickListener {
            var contact = item.contact?.replace("[^0-9]".toRegex(), "") ?: ""
            if (contact.isNotEmpty()) {
                if (contact.startsWith("0")) {
                    contact = "62" + contact.substring(1)
                } else if (!contact.startsWith("62")) {
                    contact = "62" + contact
                }

                val url = "https://api.whatsapp.com/send?phone=$contact"
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(url)
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Invalid contact number", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun getItemCount() = items.size
}