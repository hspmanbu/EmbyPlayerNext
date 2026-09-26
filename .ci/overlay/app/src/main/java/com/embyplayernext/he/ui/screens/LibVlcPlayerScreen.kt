package com.embyplayernext.he.ui.screens

import android.net.Uri
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.embyplayernext.he.data.model.EmbyServerConfig
import com.embyplayernext.he.data.model.PlaybackDescriptor
import com.embyplayernext.he.util.DiagnosticsLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

@Composable
fun LibVlcPlayerScreen(
    descriptor: PlaybackDescriptor,
    config: EmbyServerConfig,
    onStart: suspend (PlaybackDescriptor, Long, Long?) -> Unit,
    onProgress: suspend (PlaybackDescriptor, Long, Long?, Boolean, String) -> Unit,
    onStopped: suspend (PlaybackDescriptor, Long, Long?) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememerCoroutineScope()
    val log = remember { DiagnosticsLogger(context) }
    val lib = remember { LibVLC(context.applicationContext, arrayListOf("--verbose=2", "--network-caching=1500")) }
    val player = remember { MediaPlayer(lib) }
    val surface = remember { SurfaceView(context) }

    var started by remember(descriptor.playSessionId) { mutableStateOf(false) }
    var stopped by remember(descriptor.playSessionId) { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var failedžH™[Y[X™\ˆÈ]]X›TÝ]SÙŠ˜[ÙJHBˆ˜\ˆÜÈžH™[Y[X™\ˆÈ]]X›SÛ™ÔÝ]SÙŠ
HBˆ˜\ˆ\ˆžH™[Y[X™\ˆÈ]]X›SÛ™ÔÝ]SÙŠ\ØÜš\Ü‹œÙ\™\”[•[YUXÚÜÏË™]ŠLÌ
HÎˆ
HBˆ˜\ˆ›Ý]ÛÝ[žH™[Y[X™\ˆÈ]]X›R[Ý]SÙŠ
HB‚ˆ[ˆ\›

NˆÝš[™ÈÂˆYˆ
ÛÛ™šYË˜XØÙ\ÜÕÚÙ[‹š\Ð›[šÊ
JH™]\›ˆ\ØÜš\Ü‹œÝ™X[U\›ˆ˜[HH\šKœ\œÙJ\ØÜš\Ü‹œÝ™X[U\›
Bˆ™]\›ˆYˆ
K™Ù]]Y\žT\˜[Y]\Š˜\WÚÙ^HŠHOH[
H\ØÜš\Ü‹œÝ™X[U\›ˆ[ÙHK˜Z[\ÛŠ
K˜\[™]Y\žT\˜[Y]\Š˜\WÚÙ^H‹ÛÛ™šYË˜XØÙ\ÜÕÚÙ[ŠK˜Z[

KÔÝš[™Ê
BˆB‚ˆ[ˆ^]^Y\Š
HÂˆYˆ
\ÝÜY
HÂˆÝÜYHYBˆ˜[H[Ø]Ú[™ÈÈ^Y\‹[YK˜ÛÙ\˜ÙP]X\Ý

HK™Ù]Ü‘Y˜][
ÜÊBˆ˜[H[Ø]Ú[™ÈÈ^Y\‹›[™ÝZÙRYˆÈ]ˆHK™Ù]Ü“[

BˆØÛÜK›][˜ÚÂˆÛ”ÝÜY
\ØÜš\Ü‹
Bˆ[Ø]Ú[™ÈÈ^Y\‹œÝÜ

HBˆÛ‘^]

BˆBˆH[ÙHÛ‘^]

BˆB‚ˆ\ÜÜØX›QY™™XÝ
\ØÜš\Ü‹œ^TÙ\ÜÚ[Û’Y
HÂˆ˜[›Ý]H^Y\‹›Õ›Ý]ˆ›Ý]œÙ]šY[ÕšY]ÊÝ\™˜XÙJBˆ›Ý]˜]XÚšY]ÜÊ
Bˆ^Y\‹œÙ]]™[\Ý[™\ˆÈHO‚ˆÚ[ˆ
K\JHÂˆYYXT^Y\‹‘]™[“Ü[š[™ÈOˆÙË›ÙÊ“X•“È‹“Ü[š[™È][OIÙ\ØÜš\Ü‹š][KšYHŠBˆYYXT^Y\‹‘]™[”^Z[™ÈOˆØÛÜK›][˜ÚÂˆ^Z[™ÈHYBˆYˆ
\Ý\Y
HÂˆYˆ
\ØÜš\Ü‹š[š]X[ÜÚ][Û“\Èˆ
HÂˆ[^JL
Bˆ[Ø]Ú[™ÈÈ^Y\‹œÙ][YJ\ØÜš\Ü‹š[š]X[ÜÚ][Û“\ÊHBˆBˆÜÈH[Ø]Ú[™ÈÈ^Y\‹[YK˜ÛÙ\˜ÙP]X\Ý

HK™Ù]Ü‘Y˜][

Bˆ\ˆH[Ø]Ú[™ÈÈ^Y\‹›[™Ý˜ÛÙ\˜ÙP]X\Ý

HK™Ù]Ü‘Y˜][
\ŠBˆÝ\YHYBˆÙË›ÙÊ“X•“È‹”^Z[™È][OIÙ\ØÜš\Ü‹š][KšYHÜÏIÜÈ\I\ˆ›Ý]I›Ý]ÛÝ[ŠBˆÛ”Ý\
\ØÜš\Ü‹ÜË\‹ZÙRYˆÈ]ˆJBˆBˆBˆYYXT^Y\‹‘]™[”]\ÙYOˆÈ^Z[™ÈH˜[ÙNÈÙË›ÙÊ“X•“È‹”]\ÙY][OIÙ\ØÜš\Ü‹š][KšYHŠHBˆYYXT^Y\‹‘]™[•›Ý]OˆÈ›Ý]ÛÝ[HK›Ý]ÛÝ[ÈÙË›ÙÊ“X•“È‹•›Ý]][OIÙ\ØÜš\Ü‹š][KšYHÛÝ[I›Ý]ÛÝ[ŠHBˆYYXT^Y\‹‘]™[‘[˜ÛÝ[\™Y\œ›ÜˆOˆÈ˜Z[YHYNÈ^Z[™ÈH˜[ÙNÈÙË›ÙÊ“X•“È‹‘[˜ÛÝ[\™Y\œ›Üˆ][OIÙ\ØÜš\Ü‹š][KšYHÜÏIÜ[Ø]Ú[™ÈÈ^Y\‹[YHK™Ù]Ü‘Y˜][
LS
_HŠHBˆYYXT^Y\‹‘]™[‘[™™XXÚYOˆØÛÜK›][˜ÚÂˆ^Z[™ÈH˜[ÙBˆYˆ
\ÝÜY
HÂˆÝÜYHYBˆ˜[H[Ø]Ú[™ÈÈ^Y\‹[YK˜ÛÙ\˜ÙP]X\Ý

HK™Ù]Ü‘Y˜][
ÜÊBˆ˜[H[Ø]Ú[™ÈÈ^Y\‹›[™ÝZÙRYˆÈ]ˆHK™Ù]Ü“[

BˆÙË›ÙÊ“X•“È‹‘[™™XXÚY][OIÙ\ØÜš\Ü‹š][KšYHÜÏI\IŠBˆÛ”ÝÜY
\ØÜš\Ü‹
BˆBˆBˆBˆBˆÙË›ÙÊ“X•“È‹˜]XÚ][OIÙ\ØÜš\Ü‹š][KšYHÏ]YH›Ü˜ÙO]YHY]ÙIÙ\ØÜš\Ü‹œ^SY]ÙHŠBˆÛ‘\ÜÜÙHÂˆÙË›ÙÊ“X•“È‹™\ÜÜÙH][OIÙ\ØÜš\Ü‹š][KšYHŠBˆ[Ø]Ú[™ÈÈ^Y\‹œÝÜ

HBˆ[Ø]Ú[™ÈÈYˆ
›Ý]˜\™UšY]ÜÐ]XÚY

JH›Ý]™]XÚšY]ÜÊ
HBˆ[Ø]Ú[™ÈÈ^Y\‹œ™[X\ÙJ
HBˆ[Ø]Ú[™ÈÈX‹œ™[X\ÙJ
HBˆBˆB‚ˆ][˜ÚYY™™XÝ
\ØÜš\Ü‹œÝ™X[U\›\ØÜš\Ü‹œ^TÙ\ÜÚ[Û’Y
HÂˆ˜[YYXHHYYXJX‹\šKœ\œÙJ\›

JJBˆYYXKœÙ]ÑXÛÙ\‘[˜X›Y
YKYJBˆYYXK˜YÜ[ÛŠŽ›™]ÛÜšËXØXÚ[™ÏLMLŠBˆÙË›ÙÊ“X•“È‹œ™\\™H][OIÙ\ØÜš\Ü‹š][KšYH\›IÙ\ØÜš\Ü‹œÝ™X[U\›œÝXœÝš[™Ð™Y›Ü™J	ÏÉÊ_HŠBˆ^Y\‹›YYXHHYYXBˆYYXKœ™[X\ÙJ
Bˆ^Y\‹œ^J
BˆB‚ˆ][˜ÚYY™™XÝ
\ØÜš\Ü‹œ^TÙ\ÜÚ[Û’Y
HÂˆ˜\ˆ\ÝÙÈHˆÚ[H
\ÐXÝ]™H	‰ˆ\ÝÜY
HÂˆ[^JL
BˆÜÈH[Ø]Ú[™ÈÈ^Y\‹[YK˜ÛÙ\˜ÙP]X\Ý

HK™Ù]Ü‘Y˜][
ÜÊBˆ[Ø]Ú[™ÈÈ^Y\‹›[™ÝK™Ù]Ü‘Y˜][
LS
KZÙRYˆÈ]ˆOË›]È\ˆH]Bˆ˜[›ÝÈH[™›ÚY›ÜË”Þ\Ý[PÛØÚË™[\ÙY™X[[YJ
BˆYˆ
Ý\Y	‰ˆ›ÝÈH\ÝÙÈHL
HÂˆ\ÝÙÈH›ÝÂˆÙË›ÙÊ“X•“È‹šX\™X]][OIÙ\ØÜš\Ü‹š][KšYHÜÏIÜÈ\I\ˆ^Z[™ÏIÜ[Ø]Ú[™ÈÈ^Y\‹š\Ô^Z[™ÈK™Ù]Ü‘Y˜][
˜[ÙJ_H›Ý]I›Ý]ÛÝ[ŠBˆBˆBˆB‚ˆ][˜ÚYY™™XÝ
\ØÜš\Ü‹œ^TÙ\ÜÚ[Û’Y
HÂˆÚ[H
\ÐXÝ]™H	‰ˆ\ÝÜY
HÂˆ[^JLÌ
BˆYˆ
Ý\Y
HÛ”›ÙÜ™\ÜÊ\ØÜš\Ü‹ÜË\‹ZÙRYˆÈ]ˆK\[Ø]Ú[™ÈÈ^Y\‹š\Ô^Z[™ÈK™Ù]Ü‘Y˜][
˜[ÙJK•[YU\]HŠBˆBˆB‚ˆ˜XÚÒ[™\ˆÈ^]^Y\Š
HB‚ˆ›Þ
[ÙYšY\‹™š[X^Ú^™J
K˜˜XÚÙÜ›Ý[™
ÛÛÜ‹›XÚÊJHÂˆ[™›ÚYšY]Ê˜XÝÜžHHÈÝ\™˜XÙHK[ÙYšY\ˆH[ÙYšY\‹™š[X^Ú^™J
JBˆ^
“X•“È0­È9o.¹b-¹èk:)èÈ‹ÛÛÜˆHÛÛÜ‹•Ú]K[ÙYšY\ˆH[ÙYšY\‹˜[YÛŠ[YÛ›Y[•ÜÙ[\ŠK˜˜XÚÙÜ›Ý[™
ÛÛÜ‹›XÚË˜ÛÜJ[OKMYŠJKœY[™Ê™
JBˆYˆ
˜Z[Y
H^
“X•“È9¤«y¥/¹i,z-){ï&ù§*º!ê¹bª9fçº` ‹ÛÛÜˆHÛÛÜ‹•Ú]K[ÙYšY\ˆH[ÙYšY\‹˜[YÛŠ[YÛ›Y[Ù[\ŠK˜˜XÚÙÜ›Ý[™
ÛÛÜ‹›XÚË˜ÛÜJ[OKÍYŠJKœY[™ÊM‹™
JBˆ›ÝÊ[ÙYšY\‹™š[X^ÚY

K˜[YÛŠ[YÛ›Y[›ÝÛPÙ[\ŠK˜˜XÚÙÜ›Ý[™
ÛÛÜ‹›XÚË˜ÛÜJ[OKMYŠJKœY[™Ê™
JHÂˆ]ÛŠÛÛXÚÈHÈ^]^Y\Š
HJHÈ^
º/å9fçˆŠHBˆ]ÛŠÛÛXÚÈHÂˆ˜[H
[Ø]Ú[™ÈÈ^Y\‹[YHK™Ù]Ü‘Y˜][
ÜÊHHÛÛ™šYËœ™]Ú[™ÙXÛÛ™È
ˆL
K˜ÛÙ\˜ÙP]X\Ý

Bˆ[Ø]Ú[™ÈÈ^Y\‹œÙ][YJ
HBˆJHÈ^
‹IØÛÛ™šYËœ™]Ú[™ÙXÛÛ™ß\ÈŠHBˆ]ÛŠÛÛXÚÈHÈYˆ
[Ø]Ú[™ÈÈ^Y\‹š\Ô^Z[™ÈK™Ù]Ü‘Y˜][
˜[ÙJJH^Y\‹œ]\ÙJ
H[ÙH^Y\‹œ^J
HJHÈ^
Yˆ
^Z[™ÊH¹¦ ¹`gˆ[ÙH¹¤«y¥/ˆŠHBˆ]ÛŠÛÛXÚÈHÂˆ˜[H
[Ø]Ú[™ÈÈ^Y\‹[YHK™Ù]Ü‘Y˜][
ÜÊH
ÈÛÛ™šYË™›ÜØ\™ÙXÛÛ™È
ˆL
K˜ÛÙ\˜ÙP]X\Ý

Bˆ[Ø]Ú[™ÈÈ^Y\‹œÙ][YJ
HBˆJHÈ^
ŠÉØÛÛ™šYË™›ÜØ\™ÙXÛÛ™ß\ÈŠHBˆ^
‰ÜÜËÌL\ÈÈ	ÚYˆ
\Œ
H\‹ÌL[ÙH\È‹ÛÛÜPÛÛÜ‹•Ú]K[ÙYšY\S[ÙYšY\‹œY[™ÊL™
JBˆBˆBŸB