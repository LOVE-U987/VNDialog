package top.yourzi.dialog.model;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

/**
 * KUBEGS 事件配置
 */
@Setter
@Getter
public class KubegsEventInfo {
    // 触发事件类型
    @SerializedName("trigger")
    private String trigger;
    
    // 触发后的动作
    @SerializedName("action")
    private KubegsActionInfo action;
    
    public KubegsEventInfo() {}
    
    public KubegsEventInfo(String trigger, KubegsActionInfo action) {
        this.trigger = trigger;
        this.action = action;
    }
}
