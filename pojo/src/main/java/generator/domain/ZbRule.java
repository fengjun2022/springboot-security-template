package generator.domain;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import javax.validation.constraints.NotNull;

import java.io.Serializable;

import java.util.Date;
import io.swagger.annotations.ApiModelProperty;
import org.hibernate.validator.constraints.Length;

/**
* 招标规则表
* @TableName zb_rule
*/
public class ZbRule implements Serializable {

    /**
    * 
    */
    @NotNull(message="[]不能为空")
    @ApiModelProperty("")
    private Integer id;
    /**
    * 索引名称
    */
    @NotBlank(message="[索引名称]不能为空")
    @Size(max= 255,message="编码长度不能超过255")
    @ApiModelProperty("索引名称")
    @Length(max= 255,message="编码长度不能超过255")
    private String indexName;
    /**
    * 规则JSON数据
    */
    @Size(max= -1,message="编码长度不能超过-1")
    @ApiModelProperty("规则JSON数据")
    @Length(max= -1,message="编码长度不能超过-1")
    private String rule;
    /**
    * 
    */
    @NotNull(message="[]不能为空")
    @ApiModelProperty("")
    private Date createdAt;
    /**
    * 
    */
    @NotNull(message="[]不能为空")
    @ApiModelProperty("")
    private Date updatedAt;

    /**
    * 
    */
    private void setId(Integer id){
    this.id = id;
    }

    /**
    * 索引名称
    */
    private void setIndexName(String indexName){
    this.indexName = indexName;
    }

    /**
    * 规则JSON数据
    */
    private void setRule(String rule){
    this.rule = rule;
    }

    /**
    * 
    */
    private void setCreatedAt(Date createdAt){
    this.createdAt = createdAt;
    }

    /**
    * 
    */
    private void setUpdatedAt(Date updatedAt){
    this.updatedAt = updatedAt;
    }


    /**
    * 
    */
    private Integer getId(){
    return this.id;
    }

    /**
    * 索引名称
    */
    private String getIndexName(){
    return this.indexName;
    }

    /**
    * 规则JSON数据
    */
    private String getRule(){
    return this.rule;
    }

    /**
    * 
    */
    private Date getCreatedAt(){
    return this.createdAt;
    }

    /**
    * 
    */
    private Date getUpdatedAt(){
    return this.updatedAt;
    }

}
