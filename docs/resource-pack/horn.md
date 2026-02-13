# 警笛機能 (Horn)

Manual Enchanceでは、車両ごとに独自の警笛音を設定し、手動で鳴らすことができます。

## プロパティ

| キー | 型 | 説明 |
| :--- | :--- | :--- |
| `horn_sound_base_id` | String | 警笛で使用するサウンドを設定します。ここで設定するサウンドは、予め`sounds.json`で定義する必要があります。 |

## 設定方法
リソースパックの `mtr_custom_resources.json` 内、`custom_trains` の車両セクションに以下の設定を追加します。

```json
{
    "custom_trains": {
		"example":{
			"name":"example",
            /* 省略 */
			"horn_sound_base_id": "mtr:horn_1",
            /* 省略 */
		}
    }
}
```