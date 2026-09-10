package com.selyro.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.selyro.tv.data.AccountStore
import com.selyro.tv.iptv.XtreamClient
import com.selyro.tv.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(app:Application):AndroidViewModel(app){
 private val store=AccountStore(app)
 private val _account=MutableStateFlow(store.load());val account=_account.asStateFlow()
 val channels=MutableStateFlow<List<Channel>>(emptyList());val movies=MutableStateFlow<List<VodItem>>(emptyList());val series=MutableStateFlow<List<SeriesItem>>(emptyList());val loading=MutableStateFlow(false);val error=MutableStateFlow<String?>(null)
 fun login(a:PlaylistAccount){viewModelScope.launch{loading.value=true;error.value=null;val c=XtreamClient(a);if(c.authenticate()){store.save(a);_account.value=a;load()}else error.value="Server login failed";loading.value=false}}
 fun load(){val a=_account.value?:return;viewModelScope.launch{loading.value=true;runCatching{val c=XtreamClient(a);channels.value=c.live();movies.value=c.movies();series.value=c.series()}.onFailure{error.value=it.message};loading.value=false}}
 fun logout(){store.clear();_account.value=null;channels.value=emptyList();movies.value=emptyList();series.value=emptyList()}
 fun toggleFavorite(id:String){store.favorite(id,!store.favorites().contains(id))}
 fun favorites()=store.favorites()
 fun watched(id:String)=store.recent(id)
}
