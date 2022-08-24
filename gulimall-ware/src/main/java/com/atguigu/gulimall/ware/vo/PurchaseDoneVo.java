package com.atguigu.gulimall.ware.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;
@Data
public class PurchaseDoneVo {

    @NotNull
    private  Long id;//采购单id
    private List<PurchaseItemVo> items;//完成/失败的需求详情

}
