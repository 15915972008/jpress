/**
 * Copyright (c) 2016-2019, Michael Yang 杨福海 (fuhai999@gmail.com).
 * <p>
 * Licensed under the GNU Lesser General Public License (LGPL) ,Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.gnu.org/licenses/lgpl-3.0.txt
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.jpress.web.wechat;

import com.jfinal.aop.Before;
import com.jfinal.aop.Inject;
import com.jfinal.log.Log;
import com.jfinal.weixin.sdk.jfinal.MsgControllerAdapter;
import com.jfinal.weixin.sdk.jfinal.MsgInterceptor;
import com.jfinal.weixin.sdk.msg.in.InMsg;
import com.jfinal.weixin.sdk.msg.in.InTextMsg;
import com.jfinal.weixin.sdk.msg.in.event.EventInMsg;
import com.jfinal.weixin.sdk.msg.in.event.InFollowEvent;
import com.jfinal.weixin.sdk.msg.in.event.InMenuEvent;
import com.jfinal.weixin.sdk.msg.in.event.InQrCodeEvent;
import com.jfinal.weixin.sdk.msg.out.OutTextMsg;
import io.jboot.utils.StrUtil;
import io.jboot.web.controller.annotation.RequestMapping;
import io.jpress.core.wechat.WechatAddon;
import io.jpress.core.wechat.WechatAddonInfo;
import io.jpress.core.wechat.WechatAddonManager;
import io.jpress.model.WechatReply;
import io.jpress.service.OptionService;
import io.jpress.service.WechatReplyService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Michael Yang 杨福海 （fuhai999@gmail.com）
 * @version V1.0
 * @Package io.jpress.web.wechat
 */
@RequestMapping("/wechat/msg")
public class WechatMsgNotifyController extends MsgControllerAdapter {

    private static final Log LOG = Log.getLog(WechatMsgNotifyController.class);

    @Inject
    private OptionService optionService;

    @Inject
    private WechatReplyService wechatReplyService;

    @Override
    @Before(MsgInterceptor.class)
    public void index() {

        //找到可以接收该消息的微信插件
        List<WechatAddonInfo> addons = getMathingAddon();
        if (addons == null || addons.isEmpty()) {
            //找不到，走默认流程
            super.index();
            return;
        }

        for (WechatAddonInfo addon : addons) {
            try {
                //只要有一个插件成功处理，则不再让下一个插件去处理
                //但是处理不成功，下个插件继续处理该消息
                if (addon.getAddon().onRenderMessage(getInMsg(), this)) {
                    return;
                }
            } catch (Exception ex) {
                LOG.error(ex.toString(), ex);
            }
        }

        //如果所有插件都 处理不成功
        //或者插件本身不做处理，走默认流程
        super.index();
    }

    /**
     * 新用户关注了公众号
     *
     * @param inFollowEvent
     */
    @Override
    protected void processInFollowEvent(InFollowEvent inFollowEvent) {
        if (InFollowEvent.EVENT_INFOLLOW_SUBSCRIBE.equals(inFollowEvent.getEvent())) {
            renderOptionValue("wechat_reply_user_subscribe", "");
        }
    }

    /**
     * 用户扫码了带参数二维码，但是没有任何插件去处理的时候
     * 关注事件等同于关注公众号了
     *
     * @param inQrCodeEvent
     */
    @Override
    protected void processInQrCodeEvent(InQrCodeEvent inQrCodeEvent) {
        if (InQrCodeEvent.EVENT_INQRCODE_SUBSCRIBE.equals(inQrCodeEvent.getEvent())) {
            renderOptionValue("wechat_reply_user_subscribe", "");
        } else {
            super.processInQrCodeEvent(inQrCodeEvent);
        }
    }

    /**
     * 用户发送了文字消息
     *
     * @param inTextMsg
     */
    @Override
    protected void processInTextMsg(InTextMsg inTextMsg) {
        String key = inTextMsg.getContent();
        renderTextMsg(inTextMsg, key);

    }

    private void renderTextMsg(InMsg inMsg, String key) {
        if (StrUtil.isBlank(key)) {
            renderDefault();
            return;
        }

        WechatReply wechatReply = wechatReplyService.findByKey(key);
        if (wechatReply == null || StrUtil.isBlank(wechatReply.getContent())) {
            renderDefault();
            return;
        }

        OutTextMsg outTextMsg = new OutTextMsg(inMsg);
        outTextMsg.setContent(wechatReply.getContent());
        render(outTextMsg);
    }

    /**
     * 用户点击了 事件菜单（备注：微信菜单分为多种类型，其实一种是事件类型）
     *
     * @param inMenuEvent
     */
    @Override
    protected void processInMenuEvent(InMenuEvent inMenuEvent) {
        String key = inMenuEvent.getEventKey();
        renderTextMsg(inMenuEvent,key);
    }


    @Override
    protected void renderDefault() {
        //不处理事件消息，否则可能会出现用户进行扫描带参数二维码等回复此内容
        if (getInMsg() instanceof EventInMsg) {
            renderNull();
        } else {
            renderOptionValue("wechat_reply_unknow", "");
        }
    }

    private void renderOptionValue(String optionKey, String defaultText) {
        String text = optionService.findByKey(optionKey);
        if (StrUtil.isBlank(text)) {
            renderText(defaultText);
            return;
        }

        InMsg msg = getInMsg();
        OutTextMsg outMsg = new OutTextMsg(msg);
        outMsg.setContent(text);

        render(outMsg);
    }


    protected List<WechatAddonInfo> getMathingAddon() {
        Collection<WechatAddonInfo> enableAddons = WechatAddonManager.me().getEnableWechatAddons();
        if (enableAddons == null || enableAddons.isEmpty()) {
            return null;
        }

        List<WechatAddonInfo> list = new ArrayList<>();
        for (WechatAddonInfo addonInfo : enableAddons) {
            WechatAddon addon = addonInfo.getAddon();
            if (addon != null && addon.onMatchingMessage(getInMsg(), this)) {
                list.add(addonInfo);
            }
        }

        return list;
    }
}
