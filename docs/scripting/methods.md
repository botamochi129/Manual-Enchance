# JavaScript メソッド一覧

車両スクリプトで使用できる追加メソッドの一覧です。

## TrainScriptContext

- `TrainScriptContext.playHorn(): void`

警笛を再生します。
これはクライアントでのみ処理されるため、パケットは送信されません。

## Train

| プロパティ | 説明 |
| :--- | :--- |
| `train.pantographState(): int` | 0~3の範囲でパンタグラフの状態を取得します。0が下降で、順番に5m、w51、6mと続きます。 |
| `train.reverser(): int` | リバーサーの状態を取得します。1が前方(F)、0は中間(N)、-1が後方(B)です。 |

- `train.getRollsignIndex(key: String): int`

方向幕のidをkeyとして入れると、そのidの現在のindexを返します。idに対応するindexが見つからない場合は0を返します。