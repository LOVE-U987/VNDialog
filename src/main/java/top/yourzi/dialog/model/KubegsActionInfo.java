package top.yourzi.dialog.model;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

/**
 * KUBEGS 动作配置
 */
@Setter
@Getter
public class KubegsActionInfo {
    // 执行的命令
    @SerializedName("command")
    private String command;
    
    // 其他可能的动作
    @SerializedName("data")
    private List<KubegsDataAction> dataActions;
    
    public KubegsActionInfo() {}
    
    public KubegsActionInfo(String command) {
        this.command = command;
    }
}
