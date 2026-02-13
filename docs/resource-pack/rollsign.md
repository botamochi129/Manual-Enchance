# 方向幕 (Rollsign)

車両のモデルプロパティを書くjsonファイルに以下のパラメータを追加することで、方向幕を追加できます。

> [!TIP]
> この項目は、MTRのリソースパックエディターでも編集することが出来ます。

## プロパティ

| キー | 型 | 説明 |
| :--- | :--- | :--- |
| `rollsign` | boolean | 方向幕を使用するかどうかを設定します。 |
| `rollsign_steps` | int | 方向幕画像の分割数です。例えば、5に設定すると横に5分割され、キー入力で0~4の値から変更できます。 |
| `rollsign_animation` | boolean | 幕切り替え時に回転アニメーションをつけるかどうかを設定します。 |
| `rollsign_id` | String | 方向幕のidを設定します。設定がない場合はモデル名がidになります。同じidを異なるパーツに設定することで、同じidの幕は同じ動作になります。 |
| `rollsign_texture` | String | 幕で使用する画像のパスを設定します。 |

## 設定例

```json
{
    "transport_mode": "TRAIN",
    "length": 19,
    "width": 3,
    "door_max": 10,
    "parts": [
        {
            "name": "front_lcd",
            "stage": "EXTERIOR",
            "mirror": false,
            "skip_rendering_if_too_far": false,
            "door_offset": "NONE",
            "render_condition": "ALL",
            "positions": [
                [
                    0.0,
                    0.0
                ]
            ],
            "whitelisted_cars": "1",
            "blacklisted_cars": "%1",
            "rollsign": true,
            "rollsign_steps": 21,
            "rollsign_animation": false,
            "rollsign_id": "front_dest",
            "rollsign_texture": "mtr:20m_4d_straight/mtr_rollsign.png"
        }
    ]
}
```