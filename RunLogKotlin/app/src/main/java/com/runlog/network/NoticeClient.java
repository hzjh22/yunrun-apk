package com.runlog.network;

public class NoticeClient {
    public NoticeInfo fetch() {
        NoticeInfo out = new NoticeInfo();
        out.enabled = true;
        out.title = "RunLog 开源免费声明";
        out.content = "本软件完全免费，如果你付费了说明你被骗了。\n"
                + "感觉软件好用的话请点击一个 Star，谢谢。\n"
                + "开源地址：https://github.com/hzjh22/yunrun-apk";
        out.updatedAt = System.currentTimeMillis();
        out.version = "1.0";
        return out;
    }
}
