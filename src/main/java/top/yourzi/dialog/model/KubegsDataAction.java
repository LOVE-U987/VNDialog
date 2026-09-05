package top.yourzi.dialog.model;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

/**
 * KUBEGS 数据操作动作
 * 用于修改玩家 JSON 数据
 */
@Getter
@Setter
public class KubegsDataAction {
    // 操作类型：set/add/subtract/multiply
    @SerializedName("type")
    private String type;
    
    // 数据键名
    @SerializedName("key")
    private String key;
    
    // 操作值
    @SerializedName("value")
    private Double value;
    
    // 目标玩家（可选，默认为执行者）
    @SerializedName("target")
    private String target;
    
    public KubegsDataAction() {}
    
    public KubegsDataAction(String type, String key, Double value) {
        this.type = type;
        this.key = key;
        this.value = value;
    }
    
    public KubegsDataAction(String type, String key, Double value, String target) {
        this.type = type;
        this.key = key;
        this.value = value;
        this.target = target;
    }
}
