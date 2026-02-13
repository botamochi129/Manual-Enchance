# パンタグラフ(Pantograph/Pantagraph)

車両のモデルプロパティを書くjsonファイルを編集することで、簡易的なパンタグラフ動作を作ることが出来ます。

> [!TIP]
> この項目は、MTRのリソースパックエディターでも編集することが出来ます。

## プロパティ

| 値 | 説明 |
| :--- | :--- |
| `PANTAGRAPH_DOWN` | パンタグラフ下降時のみ表示します。 |
| `PANTAGRAPH_5M` | パンタグラフ上昇(5m)時のみ表示します。 |
| `PANTAGRAPH_W51` | パンタグラフ上昇(w51)時のみ表示します。 |
| `PANTAGRAPH_6M` | パンタグラフ上昇(6m)時のみ表示します。 |

## 設定例

```json
{
    "transport_mode": "TRAIN",
    "length": 19,
    "width": 3,
    "door_max": 10,
    "parts": [
        {
            "name": "panto_down",
            "stage": "EXTERIOR",
            "mirror": false,
            "skip_rendering_if_too_far": false,
            "door_offset": "NONE",
            "render_condition": "PANTAGRAPH_DOWN",
            "positions": [
                [
                    0.0,
                    0.0
                ]
            ],
            "whitelisted_cars": "1",
            "blacklisted_cars": "%1"
        },
        {
            "name": "panto_5m",
            "stage": "EXTERIOR",
            "mirror": false,
            "skip_rendering_if_too_far": false,
            "door_offset": "NONE",
            "render_condition": "PANTAGRAPH_5M",
            "positions": [
                [
                    0.0,
                    0.0
                ]
            ],
            "whitelisted_cars": "1",
            "blacklisted_cars": "%1"
        }
    ]
}
```