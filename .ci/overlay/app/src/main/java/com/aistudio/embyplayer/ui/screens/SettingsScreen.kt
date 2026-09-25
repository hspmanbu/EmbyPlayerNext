package com.aistudio.embyplayer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aistudio.embyplayer.data.model.EmbyServerConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config:EmbyServerConfig,onUpdate:(EmbyServerConfig)->Unit,onDynamic:()->Unit,onLogin:()->Unit,onLogout:()->Unit,
    onShareLog:()->Unit,onClearLog:()->Unit,onBack:()->Unit
) {
    var logoutConfirm by remember { mutableStateOf(false) }
    Scaffold(topBar={TopAppBar(title={Text("设置与显示调节")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)}})}) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item { Section("账号与服务器") }
            item { SettingRow("当前服务器", "${config.serverName}\n${config.serverUrl}", Icons.Default.Dns, onLogin) }
            item { SettingRow(if(config.accessToken.isBlank())"连接登录" else "切换服务器/重登", config.username.ifBlank{"未登录"}, Icons.Default.Login, onLogin) }
            if(config.accessToken.isNotBlank()) item { SettingRow("退出登录","退出后清除登录 Token",Icons.Default.Logout){logoutConfirm=true} }
            item { SettingRow("动态端口自动解析设置", if(config.dynamicPortEnabled)"启用 (${config.dynamicPortTargetDomain})" else "未启用",Icons.Default.SettingsEthernet,onDynamic) }

            item { Section("界面缩放调节 (车机/手机自适应)") }
            item { ChoiceChips(listOf(.85f to "85% 紧凑",1f to "100% 标准",1.15f to "115% 稍大",1.25f to "125% 车机推荐",1.4f to "140% 大号",1.6f to "160% 超大"),config.uiScale){onUpdate(config.copy(uiScale=it))} }

            item { Section("播放内核与解码") }
            item { SwitchRow("硬件加速解码 (MediaCodec)","优先使用设备 H.264/HEVC/AV1 解码器",config.hardwareDecoding){onUpdate(config.copy(hardwareDecoding=it))} }
            item { SwitchRow("允许自签名 HTTPS","仅对明确使用自签名证书的家庭服务器开启",config.allowInsecureHttps){onUpdate(config.copy(allowInsecureHttps=it))} }

            item { Section("播放控制与快进/快退步长") }
            item { IntChoice("后退步长：当前 ${config.rewindSeconds} 秒",config.rewindSeconds,listOf(5,10,15,20,30,45,60,90)){onUpdate(config.copy(rewindSeconds=it))} }
            item { IntChoice("前进步长：当前 ${config.forwardSeconds} 秒",config.forwardSeconds,listOf(5,10,15,20,30,45,60,90)){onUpdate(config.copy(forwardSeconds=it))} }
            item { IntChoice("手势全屏滑动跨度",config.gestureSeekSeconds,listOf(60,120,180,300,600),suffix={v->when(v){60->"1 分钟 (高灵敏度)";120->"2 分钟 (稍高)";180->"3 分钟 (默认标准)";300->"5 分钟 (车载推荐)";else->"10 分钟 (大跨度)"}}){onUpdate(config.copy(gestureSeekSeconds=it))} }
            item { IntChoice("本地磁盘缓冲",config.cacheMb,listOf(0,64,128,256,512),suffix={if(it==0)"已关闭 (0 MB)" else "$it MB${if(it==128)" (车机推荐)" else ""}"}){onUpdate(config.copy(cacheMb=it))} }

            item { Section("诊断与构建信息") }
            item { SettingRow("导出诊断日志","App 内直接分享 emby_diag.txt，无需 ADB",Icons.Default.Share,onShareLog) }
            item { SettingRow("清除诊断日志","清空当前诊断文件",Icons.Default.DeleteSweep,onClearLog) }
            item { ListItem(headlineContent={Text("应用版本")},supportingContent={Text("2.1.0 · 新首页/全局搜索/详情增强 · PlaybackInfo/STRM Runtime/Dynamic Port")}) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if(logoutConfirm) AlertDialog(onDismissRequest={logoutConfirm=false},title={Text("确认退出登录？")},text={Text("退出后将清除登录 Token，下次使用需重新输入密码。")},confirmButton={Button(onClick={logoutConfirm=false;onLogout()}){Text("退出")}},dismissButton={TextButton(onClick={logoutConfirm=false}){Text("取消")}})
}

@Composable private fun Section(t:String){Text(t,style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(top=10.dp,bottom=4.dp))}
@Composable private fun SettingRow(title:String,sub:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit){ListItem(headlineContent={Text(title)},supportingContent={Text(sub)},leadingContent={Icon(icon,null)},modifier=Modifier.clickable(onClick=onClick))}
@Composable private fun SwitchRow(title:String,sub:String,checked:Boolean,onChecked:(Boolean)->Unit){ListItem(headlineContent={Text(title)},supportingContent={Text(sub)},trailingContent={Switch(checked,onChecked)})}
@Composable private fun <T> ChoiceChips(options:List<Pair<T,String>>,selected:T,onSelect:(T)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){options.take(3).forEach{(v,l)->FilterChip(selected=v==selected,onClick={onSelect(v)},label={Text(l)})}};Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){options.drop(3).forEach{(v,l)->FilterChip(selected=v==selected,onClick={onSelect(v)},label={Text(l)})}}}
@Composable private fun IntChoice(title:String,current:Int,options:List<Int>,suffix:(Int)->String={it.toString()},onSelect:(Int)->Unit){var open by remember{mutableStateOf(false)};Box{ListItem(headlineContent={Text(title)},supportingContent={Text(suffix(current))},modifier=Modifier.clickable{open=true});DropdownMenu(open,{open=false}){options.forEach{v->DropdownMenuItem(text={Text(suffix(v))},onClick={open=false;onSelect(v)})}}}}